from fastapi import FastAPI, HTTPException
from src.models import RiskAssessmentRequest, RiskAssessmentResponse
from src.scorecard import process_risk_assessment
import uvicorn
from datetime import datetime

app = FastAPI(
    title="风控评分API",
    description="互联网个人贷款风控评分系统",
    version="1.0.0"
)

@app.post("/predict", response_model=RiskAssessmentResponse)
async def predict(data: RiskAssessmentRequest):
    """
    风控评估预测接口
    """
    try:
        result = process_risk_assessment(data.model_dump())
        if "error" in result:
            raise HTTPException(status_code=400, detail=result["error"])
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
async def health_check():
    """
    健康检查接口
    """
    return {
        "status": "healthy",
        "timestamp": datetime.utcnow().isoformat(),
        "service": "risk-assessment-api"
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
