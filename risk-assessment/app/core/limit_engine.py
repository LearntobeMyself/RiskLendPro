class LimitEngine:
    """
    额度计算引擎
    
    公式：基础额度(收入*倍数) + 资产加成 - 负债扣减
    """
    
    def __init__(self):
        self.income_mapping = {
            "3000以下": 3000,
            "3000-8000": 5500,
            "8000-15000": 11500,
            "15000以上": 20000
        }
    
    def calculate(self, score: float, user_data: dict, crawler_data: dict) -> int:
        """
        计算授信额度
        
        返回：授信额度（整数）
        """
        monthly_income = self.income_mapping.get(user_data.get("monthlyIncome"), 3000)
        
        if score >= 80:
            multiplier = 12
            max_limit = 500000
        elif score >= 70:
            multiplier = 10
            max_limit = 300000
        elif score >= 60:
            multiplier = 8
            max_limit = 200000
        else:
            return 0
        
        base_limit = monthly_income * multiplier
        
        has_house = user_data.get("hasHouse", False)
        has_car = user_data.get("hasCar", False)
        if has_house and has_car:
            base_limit += 80000
        elif has_house:
            base_limit += 50000
        elif has_car:
            base_limit += 20000
        
        loan_count = crawler_data.get("loan_count", 0)
        for _ in range(loan_count):
            base_limit *= 0.9
        
        overdue_count = crawler_data.get("overdue_count", 0)
        for _ in range(overdue_count):
            base_limit *= 0.85
        
        return max(0, min(int(base_limit), max_limit))