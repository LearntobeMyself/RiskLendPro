from pydantic import BaseModel, Field
from typing import List, Dict, Optional

class UserData(BaseModel):
    idCard: str = Field(description="身份证号（用于爬虫查询征信数据）")
    name: str = Field(description="用户姓名")
    phone: str = Field(description="手机号码")
    email: str = Field(description="邮箱")
    gender: int = Field(description="性别（0-女, 1-男）")
    birthday: str = Field(description="出生日期（YYYY-MM-DD）")
    education: str = Field(description="教育程度")
    marriage: str = Field(description="婚姻状况")
    jobType: str = Field(description="职业类型")
    monthlyIncome: str = Field(description="月收入（系统会与爬虫数据比对）")
    hasHouse: bool = Field(description="是否有房")
    hasCar: bool = Field(description="是否有车")
    contactPhone: str = Field(description="紧急联系人电话")

class BehaviorData(BaseModel):
    applyTime: str = Field(description="申请时间（YYYY-MM-DD HH:MM:SS）")
    isEmulator: bool = Field(description="是否使用模拟器")

class RiskAssessmentRequest(BaseModel):
    user_data: UserData
    behavior_data: BehaviorData

class ScoringDetail(BaseModel):
    item: str
    value: str
    sub_score: float
    comment: str

class ScoringDimension(BaseModel):
    score: float
    details: List[ScoringDetail]

class FusionCheckItem(BaseModel):
    dimension: str
    user_fill: str
    crawler_check: str
    status: str
    reason: str

class RiskAssessmentResponse(BaseModel):
    transaction_id: str = Field(description="交易唯一标识")
    sys_decision: str = Field(description="系统决策（APPROVE/REVIEW/REJECT）")
    total_score: float = Field(description="最终总得分（0-100）")
    credit_limit: int = Field(description="授信额度")
    risk_tags: List[str] = Field(description="风险标签")
    scoring_details: Dict[str, ScoringDimension] = Field(description="详细评分报告")
    fusion_check: List[FusionCheckItem] = Field(description="数据融合比对报告")

class CreditDataSyncRequest(BaseModel):
    id_card: str = Field(description="身份证号")
    overdue_count: int = Field(description="逾期次数")
    loan_count: int = Field(description="多头借贷平台数")
    is_blacklist: bool = Field(description="是否黑名单")
    real_monthly_income: int = Field(description="真实月收入")
    id_card_province: str = Field(description="身份证归属地")
    recent_query_count: int = Field(description="近期查询次数")

class CreditDataQueryRequest(BaseModel):
    id_card: str = Field(description="身份证号")