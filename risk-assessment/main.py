from fastapi import FastAPI, HTTPException
from app.api.routes import router
import uvicorn
from datetime import datetime
from app.database import init_db, DATABASE_AVAILABLE

app = FastAPI(
    title="风控评分API",
    description="互联网个人贷款风控评分系统 - 多源数据融合版本",
    version="2.0.0"
)

app.include_router(router)

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "timestamp": datetime.utcnow().isoformat(),
        "service": "risk-assessment-api",
        "database_available": DATABASE_AVAILABLE
    }

if __name__ == "__main__":
    init_db()
    uvicorn.run(app, host="0.0.0.0", port=8000)