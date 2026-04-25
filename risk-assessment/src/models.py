from pydantic import BaseModel, Field
from typing import List, Dict, Any

class UserData(BaseModel):
    idCard: str
    name: str
    phone: str
    email: str
    gender: int
    birthday: str
    education: str
    marriage: str
    jobType: str
    monthlyIncome: str
    hasHouse: bool
    hasCar: bool
    contactPhone: str

class MockData(BaseModel):
    isBlacklist: bool
    overdueCount: int
    loanCount: int
    recentQueryCount: int

class BehaviorData(BaseModel):
    applyTime: str
    isEmulator: bool

class RiskAssessmentRequest(BaseModel):
    user_data: UserData
    mock_data: MockData
    behavior_data: BehaviorData

class ScoringDetail(BaseModel):
    item: str
    value: str
    sub_score: float
    comment: str

class ScoringDimension(BaseModel):
    score: float
    details: List[ScoringDetail]

class FusionComparison(BaseModel):
    dimension: str
    user_fill: str
    mock_check: str
    status: str
    reason: str

class RiskAssessmentResponse(BaseModel):
    total_score: float
    sys_decision: str
    credit_limit: float
    scoring_breakdown: Dict[str, ScoringDimension]
    fusion_comparison: List[FusionComparison]
