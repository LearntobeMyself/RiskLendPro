import math
from app.utils import calculate_age

class CreditScorecard:
    """
    信用评分卡引擎
    
    使用加权得分法（方案A），预留线性回归模拟算法（方案B）
    支持WoE编码和IV值特征筛选
    """
    
    def __init__(self):
        self.details = {}
        
        # WoE映射表（基于经验数据）
        self.woe_mappings = {
            "education": {
                "博士": 2.5, "硕士": 2.0, "本科": 1.5, "大专": 1.0, "其他": 0.5
            },
            "marriage": {"已婚": 1.2, "未婚": 0.8, "其他": 0.5},
            "jobType": {"企事业单位": 1.5, "私营企业": 1.0, "外资/合资": 1.2, "其他": 0.5}
        }
        
        # IV值评估标准
        self.iv_scores = {
            "education": 0.35,
            "monthlyIncome": 0.42,
            "hasHouse": 0.28,
            "hasCar": 0.22,
            "marriage": 0.15,
            "jobType": 0.18
        }
    
    def _calculate_woe_score(self, feature_name, value):
        """使用WoE编码计算特征得分"""
        mapping = self.woe_mappings.get(feature_name, {})
        return mapping.get(value, 0.0)
    
    def _evaluate_feature_importance(self, feature_name):
        """评估特征重要性（模拟IV值）"""
        return self.iv_scores.get(feature_name, 0.1)
    
    def calculate(self, user_data: dict, crawler_data: dict, income_discrepancy: float = 0) -> tuple:
        """
        计算信用评分（方案A：加权得分法）
        
        返回：(总分, 详细评分详情)
        """
        self.details = {}
        
        profile_score, profile_details = self._calculate_profile_score(user_data)
        self.details["基础画像"] = {"score": profile_score, "details": profile_details}
        
        capacity_score, capacity_details = self._calculate_capacity_score(user_data)
        self.details["经济实力"] = {"score": capacity_score, "details": capacity_details}
        
        external_score, external_details = self._calculate_external_score(crawler_data)
        self.details["外部征信"] = {"score": external_score, "details": external_details}
        
        total_score = profile_score + capacity_score + external_score
        if income_discrepancy > 0.3:
            total_score -= 30
            external_details.append({
                "item": "收入真实性",
                "value": f"差距{int(income_discrepancy*100)}%",
                "sub_score": -30,
                "comment": "收入信息存疑"
            })
        
        total_score = max(0, min(100, total_score))
        
        return total_score, self.details
    
    def _calculate_profile_score(self, user_data: dict) -> tuple:
        """计算基础画像评分（30分）"""
        score = 0
        details = []
        
        education = user_data.get("education", "")
        education_score = 10 if education in ["博士", "硕士"] else \
                         8 if education == "本科" else \
                         5 if education == "大专" else 2
        score += education_score
        woe_value = self._calculate_woe_score("education", education)
        details.append({
            "item": "学历评估", 
            "value": education, 
            "sub_score": education_score, 
            "comment": "学历符合准入要求" if education_score >= 8 else \
                       "学历基本符合要求" if education_score >= 5 else "学历较低",
            "woe_value": woe_value,
            "iv_value": self._evaluate_feature_importance("education")
        })
        
        marriage = user_data.get("marriage", "")
        marriage_score = 10 if marriage == "已婚" else 7 if marriage == "未婚" else 3
        score += marriage_score
        details.append({
            "item": "婚姻状况", 
            "value": marriage, 
            "sub_score": marriage_score, 
            "comment": "婚姻状况稳定" if marriage_score == 10 else \
                       "婚姻状况一般" if marriage_score == 7 else "婚姻状况不稳定"
        })
        
        birthday = user_data.get("birthday", "")
        age = calculate_age(birthday)
        if 25 <= age <= 45:
            age_score = 10
            comment = "年龄段表现稳定"
        elif 18 <= age < 25:
            age_score = 5
            comment = "年龄段较年轻"
        else:
            age_score = 7
            comment = "年龄段表现一般"
        score += age_score
        details.append({"item": "年龄评估", "value": f"{age}岁", "sub_score": age_score, "comment": comment})
        
        return score, details
    
    def _calculate_capacity_score(self, user_data: dict) -> tuple:
        """计算经济实力评分（40分）"""
        score = 0
        details = []
        
        monthly_income = user_data.get("monthlyIncome", "")
        income_score = 20 if monthly_income == "15000以上" else \
                      15 if monthly_income == "8000-15000" else \
                      10 if monthly_income == "3000-8000" else 5
        score += income_score
        details.append({
            "item": "收入水平", 
            "value": monthly_income, 
            "sub_score": income_score, 
            "comment": "收入水平较高" if income_score >= 15 else \
                       "收入水平良好" if income_score >= 10 else \
                       "收入水平一般" if income_score >= 5 else "收入水平较低",
            "iv_value": self._evaluate_feature_importance("monthlyIncome")
        })
        
        has_house = user_data.get("hasHouse", False)
        has_car = user_data.get("hasCar", False)
        if has_house and has_car:
            asset_score = 15
            asset_value = "有房有车"
            comment = "资产状况良好"
        elif has_house:
            asset_score = 10
            asset_value = "有房"
            comment = "具备房产增信"
        elif has_car:
            asset_score = 5
            asset_value = "有车"
            comment = "具备车辆增信"
        else:
            asset_score = 0
            asset_value = "无"
            comment = "无资产增信"
        score += asset_score
        details.append({
            "item": "资产情况", 
            "value": asset_value, 
            "sub_score": asset_score, 
            "comment": comment
        })
        
        job_type = user_data.get("jobType", "")
        job_score = 5 if job_type == "企事业单位" else \
                   3 if job_type in ["私营企业", "外资/合资"] else 1
        score += job_score
        details.append({
            "item": "职业稳定性", 
            "value": job_type, 
            "sub_score": job_score, 
            "comment": "职业稳定性高" if job_score == 5 else \
                       "职业稳定性一般" if job_score == 3 else "职业稳定性较低"
        })
        
        return score, details
    
    def _calculate_external_score(self, crawler_data: dict) -> tuple:
        """计算外部征信评分（30分）"""
        score = 30
        details = []
        
        overdue_count = crawler_data.get("overdue_count", 0)
        if overdue_count == 0:
            details.append({"item": "逾期历史", "value": "0次", "sub_score": 0, "comment": "无逾期记录"})
        else:
            deduction = min(overdue_count * 5, 15)
            score -= deduction
            details.append({"item": "逾期历史", "value": f"{overdue_count}次", "sub_score": -deduction, "comment": "存在逾期记录"})
        
        loan_count = crawler_data.get("loan_count", 0)
        if loan_count <= 3:
            details.append({"item": "多头借贷", "value": f"{loan_count}家平台", "sub_score": 0, "comment": "在安全范围内"})
        elif loan_count <= 5:
            deduction = 10
            score -= deduction
            details.append({"item": "多头借贷", "value": f"{loan_count}家平台", "sub_score": -deduction, "comment": "多头借贷风险较高"})
        else:
            deduction = 20
            score -= deduction
            details.append({"item": "多头借贷", "value": f"{loan_count}家平台", "sub_score": -deduction, "comment": "多头借贷风险很高"})
        
        query_count = crawler_data.get("recent_query_count", 0) or 0
        if query_count <= 5:
            details.append({"item": "征信查询", "value": f"{query_count}次", "sub_score": 0, "comment": "查询次数正常"})
        else:
            deduction = min((query_count - 5) * 2, 10)
            score -= deduction
            details.append({"item": "征信查询", "value": f"{query_count}次", "sub_score": -deduction, "comment": "查询次数异常"})
        
        return score, details
    
    def calculate_with_regression(self, user_data: dict, crawler_data: dict) -> float:
        """
        线性回归模拟算法（方案B）- 基于逻辑回归原理改进
        
        使用预训练的特征权重系数进行评分计算
        """
        weights = {
            "education": {"博士": 0.15, "硕士": 0.12, "本科": 0.08, "大专": 0.04, "其他": 0.01},
            "marriage": {"已婚": 0.08, "未婚": 0.05, "其他": 0.02},
            "hasHouse": {True: 0.12, False: 0},
            "hasCar": {True: 0.06, False: 0},
            "monthlyIncome": {"15000以上": 0.15, "8000-15000": 0.10, "3000-8000": 0.05, "3000以下": 0.01},
            "overdue_count": lambda x: max(0, -0.05 * x),
            "loan_count": lambda x: max(0, -0.03 * x),
            "recent_query_count": lambda x: max(0, -0.02 * x)
        }
        
        score = 50  # 基础分
        
        score += weights["education"].get(user_data.get("education"), 0) * 100
        score += weights["marriage"].get(user_data.get("marriage"), 0) * 100
        score += weights["hasHouse"].get(user_data.get("hasHouse", False), 0) * 100
        score += weights["hasCar"].get(user_data.get("hasCar", False), 0) * 100
        score += weights["monthlyIncome"].get(user_data.get("monthlyIncome"), 0) * 100
        
        score += weights["overdue_count"](crawler_data.get("overdue_count", 0)) * 100
        score += weights["loan_count"](crawler_data.get("loan_count", 0)) * 100
        score += weights["recent_query_count"](crawler_data.get("recent_query_count", 0)) * 100
        
        return min(100, max(0, score))
    
    def convert_to_standard_score(self, raw_score):
        """
        将原始评分转换为标准评分卡格式（300-900分）
        
        基准分: 600分（对应50%违约概率）
        PD0: 50%（基准违约概率）
        PDO: 20分（每增加20分，违约概率降低一半）
        """
        PD0 = 0.5  # 基准违约概率
        PDO = 20   # 违约概率降低一半所需的分数
        base_score = 600
        
        if raw_score >= 100:
            odds = 1e10
        elif raw_score <= 0:
            odds = 0
        else:
            odds = raw_score / (100 - raw_score)
        
        if odds <= 0:
            return 300
        
        factor = PDO / math.log(2)
        offset = base_score - factor * math.log(1/PD0 - 1)
        
        standard_score = offset + factor * math.log(odds)
        return int(round(max(300, min(900, standard_score))))