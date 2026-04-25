# Python 风控评分接口设计

## 一、接口概述

本设计基于Python评分卡设计和接口文档，为授信评估模块提供风控评分服务。Python代码作为Docker容器部署，被Java代码内部调用。

## 二、接口设计

### 2.1 核心评分接口

**接口路径**: `/predict`
**请求方法**: POST
**内容类型**: application/json

#### 请求体 (Request Body)

```json
{
  "user_data": {
    "idCard": "510100199001011234",
    "name": "张三",
    "phone": "13800138000",
    "email": "zhangsan@example.com",
    "gender": 1,
    "birthday": "1990-01-01",
    "education": "硕士",
    "marriage": "已婚",
    "jobType": "企事业单位",
    "monthlyIncome": "15000以上",
    "hasHouse": true,
    "hasCar": true,
    "contactPhone": "13912345678"
  },
  "mock_data": {
    "isBlacklist": false,
    "overdueCount": 0,
    "loanCount": 2,
    "recentQueryCount": 3
  },
  "behavior_data": {
    "applyTime": "2023-10-27 14:00:00",
    "isEmulator": false
  }
}
```

**字段说明**:

| 字段                          | 类型      | 必填 | 说明                        |
| --------------------------- | ------- | -- | ------------------------- |
| user\_data                  | Object  | 是  | 用户自填数据                    |
| user\_data.idCard           | String  | 是  | 身份证号                      |
| user\_data.name             | String  | 是  | 用户姓名                      |
| user\_data.phone            | String  | 是  | 手机号码                      |
| user\_data.email            | String  | 是  | 邮箱                        |
| user\_data.gender           | Integer | 是  | 性别（0-女, 1-男）              |
| user\_data.birthday         | String  | 是  | 出生日期（YYYY-MM-DD）          |
| user\_data.education        | String  | 是  | 教育程度                      |
| user\_data.marriage         | String  | 是  | 婚姻状况                      |
| user\_data.jobType          | String  | 是  | 职业类型                      |
| user\_data.monthlyIncome    | String  | 是  | 月收入                       |
| user\_data.hasHouse         | Boolean | 是  | 是否有房                      |
| user\_data.hasCar           | Boolean | 是  | 是否有车                      |
| user\_data.contactPhone     | String  | 是  | 紧急联系人电话                   |
| mock\_data                  | Object  | 是  | 模拟征信数据                    |
| mock\_data.isBlacklist      | Boolean | 是  | 是否黑名单                     |
| mock\_data.overdueCount     | Integer | 是  | 逾期次数                      |
| mock\_data.loanCount        | Integer | 是  | 多头借贷平台数                   |
| mock\_data.recentQueryCount | Integer | 是  | 近期查询次数                    |
| behavior\_data              | Object  | 是  | 行为与环境数据                   |
| behavior\_data.applyTime    | String  | 是  | 申请时间（YYYY-MM-DD HH:MM:SS） |
| behavior\_data.isEmulator   | Boolean | 是  | 是否模拟器                     |

#### 响应体 (Response Body)

```json
{
  "total_score": 95.5,
  "sys_decision": "APPROVE",
  "credit_limit": 320000,
  "scoring_breakdown": {
    "profile_score": {
      "score": 27.0,
      "details": [
        {
          "item": "学历评估",
          "value": "硕士",
          "sub_score": 10,
          "comment": "学历符合准入要求"
        },
        {
          "item": "婚姻状况",
          "value": "已婚",
          "sub_score": 10,
          "comment": "婚姻状况稳定"
        },
        {
          "item": "年龄评估",
          "value": "33岁",
          "sub_score": 7,
          "comment": "年龄段表现一般"
        }
      ]
    },
    "capacity_score": {
      "score": 50.0,
      "details": [
        {
          "item": "收入水平",
          "value": "15000以上",
          "sub_score": 20,
          "comment": "收入水平较高"
        },
        {
          "item": "资产情况",
          "value": "有房有车",
          "sub_score": 20,
          "comment": "资产状况良好"
        },
        {
          "item": "职业稳定性",
          "value": "企事业单位",
          "sub_score": 10,
          "comment": "职业稳定性高"
        }
      ]
    },
    "external_risk_adj": {
      "score": 0,
      "details": [
        {
          "item": "多头借贷",
          "value": "2家平台",
          "sub_score": 0,
          "comment": "在安全范围内"
        },
        {
          "item": "逾期历史",
          "value": "0次",
          "sub_score": 0,
          "comment": "无逾期记录"
        },
        {
          "item": "黑名单",
          "value": "未命中",
          "sub_score": 0,
          "comment": "正常"
        }
      ]
    },
    "behavior_adj": {
      "score": 0,
      "details": [
        {
          "item": "申请时段",
          "value": "14:00",
          "sub_score": 0,
          "comment": "正常时段申请"
        },
        {
          "item": "设备风险",
          "value": "正常设备",
          "sub_score": 0,
          "comment": "设备无风险"
        }
      ]
    }
  },
  "fusion_comparison": [
    {
      "dimension": "收入真实性",
      "user_fill": "15000以上",
      "mock_check": "模拟流水校验：月收入约18000",
      "status": "SUCCESS",
      "reason": "收入信息一致"
    },
    {
      "dimension": "住房资产验证",
      "user_fill": "有房",
      "mock_check": "房管局数据：名下有1套房产",
      "status": "SUCCESS",
      "reason": "资产属实"
    },
    {
      "dimension": "多头借贷比对",
      "user_fill": "无",
      "mock_check": "外部查询：当前2家机构借款中",
      "status": "SUCCESS",
      "reason": "多头借贷在安全范围内"
    },
    {
      "dimension": "逾期记录比对",
      "user_fill": "无",
      "mock_check": "外部查询：历史0次逾期",
      "status": "SUCCESS",
      "reason": "无逾期记录"
    }
  ]
}
```

**字段说明**:

| 字段                                     | 类型      | 说明                   |
| -------------------------------------- | ------- | -------------------- |
| total\_score                           | Float   | 总评分（0-100）           |
| sys\_decision                          | String  | 系统决策（APPROVE/REJECT） |
| credit\_limit                          | Integer | 授信额度                 |
| scoring\_breakdown                     | Object  | 评分详细拆解               |
| scoring\_breakdown.profile\_score      | Object  | 基础画像评分               |
| scoring\_breakdown.capacity\_score     | Object  | 经济实力评分               |
| scoring\_breakdown.external\_risk\_adj | Object  | 外部风险调整               |
| scoring\_breakdown.behavior\_adj       | Object  | 行为调整                 |
| fusion\_comparison                     | Array   | 数据融合比对               |

### 2.2 健康检查接口

**接口路径**: `/health`
**请求方法**: GET
**内容类型**: N/A

#### 响应体 (Response Body)

```json
{
  "status": "healthy",
  "timestamp": "2023-10-27T14:00:00Z",
  "service": "risk-assessment-api"
}
```

## 三、Python 实现

### 3.1 项目结构

```
risk-assessment/
├── app.py              # 主应用文件
├── requirements.txt    # 依赖文件
├── Dockerfile         # Docker配置
└── src/
    ├── scorecard.py    # 评分卡核心逻辑
    ├── models.py       # 数据模型
    └── utils.py        # 工具函数
```

### 3.2 核心代码

#### app.py

```python
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
        result = process_risk_assessment(data.dict())
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
```

#### src/models.py

```python
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
```

#### src/scorecard.py

```python
import datetime
from src.utils import calculate_age, get_income_value

def calculate_profile_score(user_data):
    """计算基础画像评分"""
    score = 0
    
    # 学历评分
    education = user_data.get("education", "")
    if education in ["博士", "硕士"]:
        score += 10
    elif education == "本科":
        score += 8
    elif education == "大专":
        score += 5
    else:
        score += 2
    
    # 婚姻状况评分
    marriage = user_data.get("marriage", "")
    if marriage == "已婚":
        score += 10
    elif marriage == "未婚":
        score += 7
    else:
        score += 3
    
    # 年龄评分
    birthday = user_data.get("birthday", "")
    age = calculate_age(birthday)
    if 25 <= age <= 45:
        score += 10
    elif 18 <= age < 25:
        score += 5
    else:
        score += 7
    
    return score

def calculate_capacity_score(user_data):
    """计算经济实力评分"""
    score = 0
    
    # 月收入评分
    monthly_income = user_data.get("monthlyIncome", "")
    if monthly_income == "15000以上":
        score += 20
    elif monthly_income == "8000-15000":
        score += 15
    elif monthly_income == "3000-8000":
        score += 10
    else:
        score += 5
    
    # 资产情况评分
    has_house = user_data.get("hasHouse", False)
    has_car = user_data.get("hasCar", False)
    if has_house and has_car:
        score += 20
    elif has_house:
        score += 15
    elif has_car:
        score += 5
    
    # 职业稳定性评分
    job_type = user_data.get("jobType", "")
    if job_type == "企事业单位":
        score += 10
    elif job_type in ["私营企业", "外资/合资"]:
        score += 7
    else:
        score += 3
    
    return score

def calculate_external_adjustment(mock_data, behavior_data):
    """计算外部数据调整分数"""
    adjustment = 0
    
    # 多头借贷调整
    loan_count = mock_data.get("loanCount", 0)
    if loan_count > 3:
        adjustment -= 20
    
    # 逾期历史调整
    overdue_count = mock_data.get("overdueCount", 0)
    adjustment -= overdue_count * 15
    
    # 设备风险调整
    is_emulator = behavior_data.get("isEmulator", False)
    if is_emulator:
        adjustment -= 50
    
    # 申请时段调整
    apply_time = behavior_data.get("applyTime", "")
    hour = int(apply_time.split(" ")[1].split(":")[0]) if apply_time else 0
    if 1 <= hour <= 5:
        adjustment -= 5
    
    return adjustment

def determine_decision(score, mock_data):
    """确定系统决策"""
    if mock_data.get("isBlacklist", False):
        return "REJECT"
    
    if score >= 80:
        return "APPROVE"
    elif score >= 60:
        return "REVIEW"
    else:
        return "REJECT"

def calculate_credit_limit(score, user_data, mock_data):
    """计算授信额度"""
    # 月收入映射
    income_mapping = {
        "3000以下": 2000,
        "3000-8000": 5500,
        "8000-15000": 11500,
        "15000以上": 20000
    }
    
    monthly_income = income_mapping.get(user_data.get("monthlyIncome"), 2000)
    
    # 基础额度计算
    if score >= 80:
        base_limit = monthly_income * 12
        max_limit = 500000
    elif score >= 70:
        base_limit = monthly_income * 10
        max_limit = 300000
    elif score >= 60:
        base_limit = monthly_income * 8
        max_limit = 200000
    else:
        return 0
    
    # 资产加成
    has_house = user_data.get("hasHouse", False)
    has_car = user_data.get("hasCar", False)
    if has_house and has_car:
        base_limit += 80000
    elif has_house:
        base_limit += 50000
    elif has_car:
        base_limit += 20000
    
    # 负债扣减
    loan_count = mock_data.get("loanCount", 0)
    overdue_count = mock_data.get("overdueCount", 0)
    
    # 多头借贷扣减
    for _ in range(loan_count):
        base_limit *= 0.9
    
    # 逾期记录扣减
    for _ in range(overdue_count):
        base_limit *= 0.85
    
    # 确保额度不为负
    base_limit = max(0, base_limit)
    
    # 不超过最高额度
    return min(round(base_limit), max_limit)

def generate_scoring_breakdown(user_data, mock_data, behavior_data, score_result):
    """生成评分详细报告"""
    # 基础画像详细评分
    profile_details = []
    
    # 学历评估
    education = user_data.get("education", "")
    if education in ["博士", "硕士"]:
        profile_details.append({"item": "学历评估", "value": education, "sub_score": 10, "comment": "学历符合准入要求"})
    elif education == "本科":
        profile_details.append({"item": "学历评估", "value": education, "sub_score": 8, "comment": "学历符合准入要求"})
    elif education == "大专":
        profile_details.append({"item": "学历评估", "value": education, "sub_score": 5, "comment": "学历基本符合要求"})
    else:
        profile_details.append({"item": "学历评估", "value": education, "sub_score": 2, "comment": "学历较低"})
    
    # 婚姻状况评估
    marriage = user_data.get("marriage", "")
    if marriage == "已婚":
        profile_details.append({"item": "婚姻状况", "value": marriage, "sub_score": 10, "comment": "婚姻状况稳定"})
    elif marriage == "未婚":
        profile_details.append({"item": "婚姻状况", "value": marriage, "sub_score": 7, "comment": "婚姻状况一般"})
    else:
        profile_details.append({"item": "婚姻状况", "value": marriage, "sub_score": 3, "comment": "婚姻状况不稳定"})
    
    # 年龄评估
    birthday = user_data.get("birthday", "")
    age = calculate_age(birthday)
    if 25 <= age <= 45:
        profile_details.append({"item": "年龄评估", "value": f"{age}岁", "sub_score": 10, "comment": "年龄段表现稳定"})
    elif 18 <= age < 25:
        profile_details.append({"item": "年龄评估", "value": f"{age}岁", "sub_score": 5, "comment": "年龄段较年轻"})
    else:
        profile_details.append({"item": "年龄评估", "value": f"{age}岁", "sub_score": 7, "comment": "年龄段表现一般"})
    
    # 经济实力详细评分
    capacity_details = []
    
    # 收入水平评估
    monthly_income = user_data.get("monthlyIncome", "")
    if monthly_income == "15000以上":
        capacity_details.append({"item": "收入水平", "value": monthly_income, "sub_score": 20, "comment": "收入水平较高"})
    elif monthly_income == "8000-15000":
        capacity_details.append({"item": "收入水平", "value": monthly_income, "sub_score": 15, "comment": "收入水平良好"})
    elif monthly_income == "3000-8000":
        capacity_details.append({"item": "收入水平", "value": monthly_income, "sub_score": 10, "comment": "收入水平一般"})
    else:
        capacity_details.append({"item": "收入水平", "value": monthly_income, "sub_score": 5, "comment": "收入水平较低"})
    
    # 资产情况评估
    has_house = user_data.get("hasHouse", False)
    has_car = user_data.get("hasCar", False)
    if has_house and has_car:
        capacity_details.append({"item": "资产情况", "value": "有房有车", "sub_score": 20, "comment": "资产状况良好"})
    elif has_house:
        capacity_details.append({"item": "资产情况", "value": "有房", "sub_score": 15, "comment": "具备房产增信"})
    elif has_car:
        capacity_details.append({"item": "资产情况", "value": "有车", "sub_score": 5, "comment": "具备车辆增信"})
    else:
        capacity_details.append({"item": "资产情况", "value": "无", "sub_score": 0, "comment": "无资产增信"})
    
    # 职业稳定性评估
    job_type = user_data.get("jobType", "")
    if job_type == "企事业单位":
        capacity_details.append({"item": "职业稳定性", "value": job_type, "sub_score": 10, "comment": "职业稳定性高"})
    elif job_type in ["私营企业", "外资/合资"]:
        capacity_details.append({"item": "职业稳定性", "value": job_type, "sub_score": 7, "comment": "职业稳定性一般"})
    else:
        capacity_details.append({"item": "职业稳定性", "value": job_type, "sub_score": 3, "comment": "职业稳定性较低"})
    
    # 外部风险调整详细评分
    external_details = []
    
    # 多头借贷评估
    loan_count = mock_data.get("loanCount", 0)
    if loan_count > 3:
        external_details.append({"item": "多头借贷", "value": f"{loan_count}家平台", "sub_score": -20, "comment": "多头借贷风险较高"})
    else:
        external_details.append({"item": "多头借贷", "value": f"{loan_count}家平台", "sub_score": 0, "comment": "在安全范围内"})
    
    # 逾期历史评估
    overdue_count = mock_data.get("overdueCount", 0)
    if overdue_count > 0:
        external_details.append({"item": "逾期历史", "value": f"{overdue_count}次", "sub_score": -15 * overdue_count, "comment": "存在逾期记录"})
    else:
        external_details.append({"item": "逾期历史", "value": "0次", "sub_score": 0, "comment": "无逾期记录"})
    
    # 黑名单评估
    is_blacklist = mock_data.get("isBlacklist", False)
    if is_blacklist:
        external_details.append({"item": "黑名单", "value": "命中", "sub_score": 0, "comment": "命中黑名单"})
    else:
        external_details.append({"item": "黑名单", "value": "未命中", "sub_score": 0, "comment": "正常"})
    
    # 行为调整详细评分
    behavior_details = []
    
    # 申请时段评估
    apply_time = behavior_data.get("applyTime", "")
    hour = int(apply_time.split(" ")[1].split(":")[0]) if apply_time else 0
    if 1 <= hour <= 5:
        behavior_details.append({"item": "申请时段", "value": f"{hour}:00", "sub_score": -5, "comment": "非营业时间申请"})
    else:
        behavior_details.append({"item": "申请时段", "value": f"{hour}:00", "sub_score": 0, "comment": "正常时段申请"})
    
    # 设备风险评估
    is_emulator = behavior_data.get("isEmulator", False)
    if is_emulator:
        behavior_details.append({"item": "设备风险", "value": "模拟器", "sub_score": -50, "comment": "设备存在风险"})
    else:
        behavior_details.append({"item": "设备风险", "value": "正常设备", "sub_score": 0, "comment": "设备无风险"})
    
    return {
        "profile_score": {
            "score": score_result["profile_score"],
            "details": profile_details
        },
        "capacity_score": {
            "score": score_result["capacity_score"],
            "details": capacity_details
        },
        "external_risk_adj": {
            "score": score_result["external_adj"],
            "details": external_details
        },
        "behavior_adj": {
            "score": 0,  # 行为调整已包含在外部风险调整中
            "details": behavior_details
        }
    }

def generate_fusion_comparison(user_data, mock_data):
    """生成数据融合比对报告"""
    comparison = []
    
    # 收入真实性比对
    monthly_income = user_data.get("monthlyIncome", "")
    if monthly_income == "15000以上":
        mock_income = "模拟流水校验：月收入约18000"
        status = "SUCCESS"
        reason = "收入信息一致"
    else:
        mock_income = f"模拟流水校验：月收入约{get_income_value(monthly_income)}"
        status = "SUCCESS"
        reason = "收入信息一致"
    
    comparison.append({
        "dimension": "收入真实性",
        "user_fill": monthly_income,
        "mock_check": mock_income,
        "status": status,
        "reason": reason
    })
    
    # 住房资产验证
    has_house = user_data.get("hasHouse", False)
    if has_house:
        mock_house = "房管局数据：名下有1套房产"
        status = "SUCCESS"
        reason = "资产属实"
    else:
        mock_house = "房管局数据：名下无房产"
        status = "SUCCESS"
        reason = "资产属实"
    
    comparison.append({
        "dimension": "住房资产验证",
        "user_fill": "有房" if has_house else "无",
        "mock_check": mock_house,
        "status": status,
        "reason": reason
    })
    
    # 多头借贷比对
    loan_count = mock_data.get("loanCount", 0)
    if loan_count > 0:
        status = "WARNING" if loan_count > 3 else "SUCCESS"
        reason = "存在多头借贷" if loan_count > 3 else "多头借贷在安全范围内"
    else:
        status = "SUCCESS"
        reason = "无多头借贷"
    
    comparison.append({
        "dimension": "多头借贷比对",
        "user_fill": "无" if loan_count == 0 else "有",
        "mock_check": f"外部查询：当前{loan_count}家机构借款中",
        "status": status,
        "reason": reason
    })
    
    # 逾期记录比对
    overdue_count = mock_data.get("overdueCount", 0)
    if overdue_count > 0:
        status = "WARNING"
        reason = f"存在{overdue_count}次逾期记录"
    else:
        status = "SUCCESS"
        reason = "无逾期记录"
    
    comparison.append({
        "dimension": "逾期记录比对",
        "user_fill": "无",
        "mock_check": f"外部查询：历史{overdue_count}次逾期",
        "status": status,
        "reason": reason
    })
    
    return comparison

def check_knockout_rules(user_data, mock_data, behavior_data):
    """检查一票否决项"""
    # 黑名单命中
    if mock_data.get("isBlacklist", False):
        return True
    
    # 身份异常：紧急联系人电话与申请人手机号一致
    if user_data.get("phone") == user_data.get("contactPhone"):
        return True
    
    # 年龄准入：年龄 < 18 岁
    birthday = user_data.get("birthday", "")
    age = calculate_age(birthday)
    if age < 18:
        return True
    
    # 设备异常：检测为模拟器
    if behavior_data.get("isEmulator", False):
        return True
    
    return False

def calculate_score(user_data, mock_data, behavior_data):
    """计算用户评分"""
    # 基础画像评分
    profile_score = calculate_profile_score(user_data)
    
    # 经济实力评分
    capacity_score = calculate_capacity_score(user_data)
    
    # 外部数据评分调整
    external_adj = calculate_external_adjustment(mock_data, behavior_data)
    
    # 计算总分
    total_score = profile_score + capacity_score + external_adj
    total_score = max(0, min(100, total_score))
    
    # 确定系统决策
    sys_decision = determine_decision(total_score, mock_data)
    
    return {
        "total_score": round(total_score, 2),
        "profile_score": round(profile_score, 2),
        "capacity_score": round(capacity_score, 2),
        "external_adj": round(external_adj, 2),
        "sys_decision": sys_decision
    }

def process_risk_assessment(data):
    """处理风控评估请求"""
    try:
        # 解析数据
        user_data = data.get("user_data", {})
        mock_data = data.get("mock_data", {})
        behavior_data = data.get("behavior_data", {})
        
        # 检查一票否决项
        if check_knockout_rules(user_data, mock_data, behavior_data):
            return {
                "total_score": 0,
                "sys_decision": "REJECT",
                "credit_limit": 0,
                "scoring_breakdown": {
                    "profile_score": {"score": 0, "details": []},
                    "capacity_score": {"score": 0, "details": []},
                    "external_risk_adj": {"score": 0, "details": []},
                    "behavior_adj": {"score": 0, "details": []}
                },
                "fusion_comparison": []
            }
        
        # 计算评分
        score_result = calculate_score(user_data, mock_data, behavior_data)
        
        # 计算额度
        credit_limit = calculate_credit_limit(score_result["total_score"], user_data, mock_data)
        
        # 生成详细报告
        scoring_breakdown = generate_scoring_breakdown(user_data, mock_data, behavior_data, score_result)
        fusion_comparison = generate_fusion_comparison(user_data, mock_data)
        
        return {
            "total_score": score_result["total_score"],
            "sys_decision": score_result["sys_decision"],
            "credit_limit": credit_limit,
            "scoring_breakdown": scoring_breakdown,
            "fusion_comparison": fusion_comparison
        }
    except Exception as e:
        return {
            "error": str(e),
            "total_score": 0,
            "sys_decision": "REJECT",
            "credit_limit": 0
        }
```

#### src/utils.py

```python
import datetime

def calculate_age(birthday_str):
    """根据出生日期计算年龄"""
    try:
        birthday = datetime.datetime.strptime(birthday_str, "%Y-%m-%d")
        today = datetime.datetime.now()
        age = today.year - birthday.year - ((today.month, today.day) < (birthday.month, birthday.day))
        return age
    except:
        return 0

def get_income_value(income_level):
    """根据收入等级获取映射值"""
    income_mapping = {
        "3000以下": 2000,
        "3000-8000": 5500,
        "8000-15000": 11500,
        "15000以上": 20000
    }
    return income_mapping.get(income_level, 2000)
```

### 3.3 依赖文件

#### requirements.txt

```
fastapi==0.95.2
uvicorn==0.22.0
pydantic==2.0.3
python-dotenv==1.0.0
```

### 3.4 Docker配置

#### Dockerfile

```dockerfile
FROM python:3.9-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ../FinancialSystem .

EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

## 四、部署说明

### 4.1 构建Docker镜像

```bash
# 进入项目目录
cd risk-assessment

# 构建镜像
docker build -t risk-assessment-api .

# 运行容器
docker run -d -p 8000:8000 --name risk-assessment risk-assessment-api

# 查看日志
docker logs -f risk-assessment
```

### 4.2 健康检查

```bash
curl http://localhost:8000/health
```

### 4.3 测试接口

```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_data": {
      "idCard": "510100199001011234",
      "name": "张三",
      "phone": "13800138000",
      "email": "zhangsan@example.com",
      "gender": 1,
      "birthday": "1990-01-01",
      "education": "硕士",
      "marriage": "已婚",
      "jobType": "企事业单位",
      "monthlyIncome": "15000以上",
      "hasHouse": true,
      "hasCar": true,
      "contactPhone": "13912345678"
    },
    "mock_data": {
      "isBlacklist": false,
      "overdueCount": 0,
      "loanCount": 2,
      "recentQueryCount": 3
    },
    "behavior_data": {
      "applyTime": "2023-10-27 14:00:00",
      "isEmulator": false
    }
  }'
```

## 五、Java 调用示例

### 5.1 依赖配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### 5.2 调用代码

```java
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class RiskAssessmentClient {
    
    private static final String RISK_API_URL = "http://risk-assessment:8000/predict";
    private final RestTemplate restTemplate;
    
    public RiskAssessmentClient() {
        this.restTemplate = new RestTemplate();
    }
    
    public Map<String, Object> assessRisk(Map<String, Object> requestData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);
        
        return restTemplate.postForObject(RISK_API_URL, entity, Map.class);
    }
    
    public static void main(String[] args) {
        RiskAssessmentClient client = new RiskAssessmentClient();
        
        // 构建请求数据
        Map<String, Object> requestData = new HashMap<>();
        
        // 用户数据
        Map<String, Object> userData = new HashMap<>();
        userData.put("idCard", "510100199001011234");
        userData.put("name", "张三");
        userData.put("phone", "13800138000");
        userData.put("email", "zhangsan@example.com");
        userData.put("gender", 1);
        userData.put("birthday", "1990-01-01");
        userData.put("education", "硕士");
        userData.put("marriage", "已婚");
        userData.put("jobType", "企事业单位");
        userData.put("monthlyIncome", "15000以上");
        userData.put("hasHouse", true);
        userData.put("hasCar", true);
        userData.put("contactPhone", "13912345678");
        
        // 模拟征信数据
        Map<String, Object> mockData = new HashMap<>();
        mockData.put("isBlacklist", false);
        mockData.put("overdueCount", 0);
        mockData.put("loanCount", 2);
        mockData.put("recentQueryCount", 3);
        
        // 行为数据
        Map<String, Object> behaviorData = new HashMap<>();
        behaviorData.put("applyTime", "2023-10-27 14:00:00");
        behaviorData.put("isEmulator", false);
        
        requestData.put("user_data", userData);
        requestData.put("mock_data", mockData);
        requestData.put("behavior_data", behaviorData);
        
        // 调用风控API
        Map<String, Object> result = client.assessRisk(requestData);
        System.out.println("Risk assessment result: " + result);
    }
}
```

## 六、接口调用流程

1. **Java 端准备数据**：收集用户自填数据、模拟征信数据和行为数据
2. **构建请求体**：按照接口要求构建JSON请求体
3. **调用Python API**：通过HTTP POST请求调用 `/predict` 接口
4. **处理响应**：解析Python返回的评分结果和额度信息
5. **业务处理**：根据评分结果进行业务决策（自动审批/人工复核/拒绝）
6. **返回结果**：将结果返回给前端或存储到数据库

## 七、注意事项

1. **容器网络**：确保Java和Python容器在同一网络中，可通过Docker Compose配置
2. **超时设置**：设置合理的HTTP请求超时时间，避免评分过程过长影响用户体验
3. **错误处理**：实现完善的错误处理机制，确保API调用失败时能够优雅降级
4. **数据安全**：确保传输数据的安全性，可考虑添加认证机制
5. **监控告警**：监控API调用情况，设置合理的告警机制

## 八、总结

本设计实现了一个完整的Python风控评分接口，与Java系统集成，为授信评估模块提供风险评估服务。接口设计符合工业级标准，包含完整的请求响应结构、错误处理和部署方案。

通过Docker容器化部署，实现了服务的隔离和可扩展性，便于在不同环境中快速部署和维护。
