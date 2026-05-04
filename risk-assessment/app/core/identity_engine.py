from app.utils import calculate_age

class IdentityEngine:
    """
    身份准入引擎 - 处理一票否决规则
    
    检查：年龄、黑名单、身份一致性
    """
    
    def __init__(self):
        self.reject_reasons = []
    
    def check(self, user_data: dict, crawler_data: dict) -> bool:
        """
        执行准入检查
        
        返回：True-通过检查，False-被拒绝
        """
        self.reject_reasons = []
        
        if not self._check_age(user_data):
            return False
        
        if not self._check_blacklist(crawler_data):
            return False
        
        if not self._check_identity_consistency(user_data):
            return False
        
        return True
    
    def _check_age(self, user_data: dict) -> bool:
        """检查年龄（未满18岁拒绝）"""
        birthday = user_data.get("birthday", "")
        age = calculate_age(birthday)
        if age < 18:
            self.reject_reasons.append("年龄未满18岁")
            return False
        return True
    
    def _check_blacklist(self, crawler_data: dict) -> bool:
        """检查黑名单（命中拒绝）"""
        if crawler_data.get("is_blacklist", False):
            self.reject_reasons.append("命中行业黑名单")
            return False
        return True
    
    def _check_identity_consistency(self, user_data: dict) -> bool:
        """检查身份一致性（姓名身份证不符拒绝）"""
        if user_data.get("name") == "" or user_data.get("idCard") == "":
            self.reject_reasons.append("身份信息不完整")
            return False
        return True
    
    def get_reject_reasons(self) -> list:
        """获取拒绝原因"""
        return self.reject_reasons