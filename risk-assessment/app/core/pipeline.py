import uuid
from app.services.crawler import CreditCrawlerService
from app.core.identity_engine import IdentityEngine
from app.core.fraud_engine import FraudEngine
from app.core.credit_scorecard import CreditScorecard
from app.core.limit_engine import LimitEngine
from app.core.fusion import FusionEngine
from app.api.models import RiskAssessmentRequest, RiskAssessmentResponse

class RiskAssessmentPipeline:
    """
    风控评估管道
    
    协调各引擎执行顺序：
    1. 爬取征信数据
    2. 准入检查（一票否决）
    3. 反欺诈分析
    4. 数据融合比对
    5. 信用评分（支持加权得分法和线性回归模拟）
    6. 额度计算
    """
    
    def __init__(self):
        self.crawler = CreditCrawlerService()
        self.identity_engine = IdentityEngine()
        self.fraud_engine = FraudEngine()
        self.scorecard = CreditScorecard()
        self.limit_engine = LimitEngine()
        self.fusion_engine = FusionEngine()
    
    def process(self, request: RiskAssessmentRequest) -> RiskAssessmentResponse:
        """
        执行完整的风控评估流程
        
        返回：RiskAssessmentResponse
        """
        transaction_id = f"TXN{uuid.uuid4().hex[:12].upper()}"
        
        crawler_data = self.crawler.crawl_credit_data(request.user_data.idCard)
        
        if not self.identity_engine.check(dict(request.user_data), crawler_data):
            return RiskAssessmentResponse(
                transaction_id=transaction_id,
                sys_decision="REJECT",
                total_score=0,
                credit_limit=0,
                risk_tags=self.identity_engine.get_reject_reasons(),
                scoring_details={},
                fusion_check=[]
            )
        
        fraud_tags, fraud_score = self.fraud_engine.analyze(dict(request.behavior_data))
        
        fusion_check, income_discrepancy = self.fusion_engine.compare(
            dict(request.user_data), crawler_data
        )
        
        credit_score, scoring_details = self.scorecard.calculate(
            dict(request.user_data), crawler_data, income_discrepancy
        )
        
        credit_limit = self.limit_engine.calculate(credit_score, dict(request.user_data), crawler_data)
        
        sys_decision = self._determine_decision(credit_score, fraud_score)
        
        risk_tags = fraud_tags.copy()
        if income_discrepancy > 0.3:
            risk_tags.append("收入存疑")
        
        return RiskAssessmentResponse(
            transaction_id=transaction_id,
            sys_decision=sys_decision,
            total_score=credit_score,
            credit_limit=credit_limit,
            risk_tags=risk_tags,
            scoring_details=scoring_details,
            fusion_check=fusion_check
        )
    
    def process_with_regression(self, request: RiskAssessmentRequest) -> RiskAssessmentResponse:
        """
        使用线性回归模拟算法（方案B）执行风控评估
        
        返回：RiskAssessmentResponse
        """
        transaction_id = f"TXN{uuid.uuid4().hex[:12].upper()}"
        
        crawler_data = self.crawler.crawl_credit_data(request.user_data.idCard)
        
        if not self.identity_engine.check(dict(request.user_data), crawler_data):
            return RiskAssessmentResponse(
                transaction_id=transaction_id,
                sys_decision="REJECT",
                total_score=0,
                credit_limit=0,
                risk_tags=self.identity_engine.get_reject_reasons(),
                scoring_details={},
                fusion_check=[]
            )
        
        fraud_tags, fraud_score = self.fraud_engine.analyze(dict(request.behavior_data))
        
        fusion_check, income_discrepancy = self.fusion_engine.compare(
            dict(request.user_data), crawler_data
        )
        
        credit_score = self.scorecard.calculate_with_regression(
            dict(request.user_data), crawler_data
        )
        
        if income_discrepancy > 0.3:
            credit_score = max(0, credit_score - 30)
        
        credit_limit = self.limit_engine.calculate(credit_score, dict(request.user_data), crawler_data)
        
        sys_decision = self._determine_decision(credit_score, fraud_score)
        
        risk_tags = fraud_tags.copy()
        if income_discrepancy > 0.3:
            risk_tags.append("收入存疑")
        
        return RiskAssessmentResponse(
            transaction_id=transaction_id,
            sys_decision=sys_decision,
            total_score=credit_score,
            credit_limit=credit_limit,
            risk_tags=risk_tags,
            scoring_details={"algorithm": "linear_regression"},
            fusion_check=fusion_check
        )
    
    def _determine_decision(self, credit_score: float, fraud_score: float) -> str:
        """
        根据评分确定决策
        
        - APPROVE: 信用评分>=80且反欺诈评分>=80
        - REVIEW: 信用评分>=60或反欺诈评分>=60
        - REJECT: 其他情况
        """
        if credit_score >= 80 and fraud_score >= 80:
            return "APPROVE"
        elif credit_score >= 60 or fraud_score >= 60:
            return "REVIEW"
        else:
            return "REJECT"