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
