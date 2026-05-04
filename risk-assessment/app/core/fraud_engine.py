class FraudEngine:
    """
    反欺诈引擎 - 分析行为数据
    
    检测：模拟器、申请时间、设备异常频率
    """
    
    def __init__(self):
        self.risk_tags = []
        self.score = 0
    
    def analyze(self, behavior_data: dict) -> tuple:
        """
        执行反欺诈分析
        
        返回：(风险标签列表, 行为评分)
        """
        self.risk_tags = []
        self.score = 100
        
        self._check_emulator(behavior_data)
        self._check_apply_time(behavior_data)
        self._check_device_risk(behavior_data)
        
        return self.risk_tags, self.score
    
    def _check_emulator(self, behavior_data: dict):
        """检测是否使用模拟器"""
        if behavior_data.get("isEmulator", False):
            self.risk_tags.append("疑似模拟器")
            self.score -= 50
    
    def _check_apply_time(self, behavior_data: dict):
        """检测申请时间是否在凌晨（1-5点）"""
        apply_time = behavior_data.get("applyTime", "")
        if apply_time:
            hour = int(apply_time.split(" ")[1].split(":")[0])
            if 1 <= hour <= 5:
                self.risk_tags.append("深夜申请")
                self.score -= 10
    
    def _check_device_risk(self, behavior_data: dict):
        """检测设备异常频率（模拟检测）"""
        pass