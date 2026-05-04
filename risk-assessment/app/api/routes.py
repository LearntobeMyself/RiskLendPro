from fastapi import APIRouter, HTTPException
from app.api.models import RiskAssessmentRequest, RiskAssessmentResponse, CreditDataSyncRequest, CreditDataQueryRequest
from app.core.pipeline import RiskAssessmentPipeline
from app.services.crawler import CreditCrawlerService
from app.database import get_db, DATABASE_AVAILABLE
from app.database.models import CreditData, Blacklist
from typing import Optional

router = APIRouter()
pipeline = RiskAssessmentPipeline()
crawler = CreditCrawlerService()

@router.post("/predict", response_model=RiskAssessmentResponse, summary="风控评估预测（加权得分法）")
async def predict(data: RiskAssessmentRequest):
    """
    使用加权得分法（方案A）进行风控评估
    
    - 接收用户数据和行为数据
    - 自动爬取征信数据（优先从数据库获取，不可用时回退到模拟数据）
    - 执行准入检查、反欺诈分析、数据融合比对
    - 返回评分结果、风险标签和授信额度
    """
    try:
        result = pipeline.process(data)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/predict/regression", response_model=RiskAssessmentResponse, summary="风控评估预测（线性回归法）")
async def predict_regression(data: RiskAssessmentRequest):
    """
    使用线性回归模拟算法（方案B）进行风控评估
    
    - 使用预训练的特征权重系数进行评分计算
    - 基础分为50分，各特征权重相加得到最终评分
    - 返回评分结果、风险标签和授信额度
    """
    try:
        result = pipeline.process_with_regression(data)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/credit/sync", summary="同步征信数据到数据库")
async def sync_credit_data(request: CreditDataSyncRequest):
    """
    将外部获取的征信数据同步到本地数据库
    
    - 支持更新或插入征信数据
    - 模拟从第三方征信库获取数据后同步的过程
    """
    if not DATABASE_AVAILABLE:
        raise HTTPException(status_code=503, detail="数据库不可用")
    
    try:
        crawler.sync_credit_data(request.id_card, {
            "overdue_count": request.overdue_count,
            "loan_count": request.loan_count,
            "is_blacklist": request.is_blacklist,
            "real_monthly_income": request.real_monthly_income,
            "id_card_province": request.id_card_province,
            "recent_query_count": request.recent_query_count
        })
        return {"message": "征信数据同步成功", "id_card": request.id_card}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/credit/query", summary="查询征信数据")
async def query_credit_data(request: CreditDataQueryRequest):
    """
    查询指定身份证号的征信数据
    
    - 优先从数据库查询
    - 如果数据库中不存在且允许回退，则生成模拟数据
    """
    try:
        credit_data = crawler.crawl_credit_data(request.id_card)
        return {"id_card": request.id_card, "credit_data": credit_data}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/credit/list", summary="获取所有征信数据列表")
async def list_credit_data(limit: Optional[int] = 10, offset: Optional[int] = 0):
    """
    获取数据库中的征信数据列表（仅当数据库可用时）
    """
    if not DATABASE_AVAILABLE:
        raise HTTPException(status_code=503, detail="数据库不可用")
    
    try:
        db = next(get_db(), None)
        if db is None:
            raise HTTPException(status_code=503, detail="无法获取数据库连接")
        
        records = db.query(CreditData).offset(offset).limit(limit).all()
        return {
            "count": len(records),
            "data": [record.to_dict() for record in records]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))