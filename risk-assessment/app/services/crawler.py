import random
import hashlib
import os
from dotenv import load_dotenv
import mysql.connector
from mysql.connector import Error

load_dotenv()

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", 3306)),
    "user": os.getenv("DB_USER", "admin"),
    "password": os.getenv("DB_PASSWORD", "admin"),
    "database": os.getenv("DB_NAME", "credit_data_db")
}

class CreditCrawlerService:
    def __init__(self):
        self.db_available = self._check_db_connection()
    
    def _check_db_connection(self):
        try:
            conn = mysql.connector.connect(**DB_CONFIG)
            if conn.is_connected():
                conn.close()
                return True
        except Error:
            pass
        return False
    
    def _get_db_connection(self):
        try:
            return mysql.connector.connect(**DB_CONFIG)
        except Error:
            return None
    
    def crawl_credit_data(self, id_card: str) -> dict:
        if self.db_available:
            try:
                db_data = self._fetch_from_database(id_card)
                if db_data:
                    return db_data
            except Exception as e:
                print(f"数据库查询失败，回退到模拟数据: {str(e)}")
        
        return self._generate_mock_data(id_card)
    
    def _fetch_from_database(self, id_card: str) -> dict:
        conn = self._get_db_connection()
        if not conn:
            return None
        
        try:
            cursor = conn.cursor(dictionary=True)
            
            cursor.execute("SELECT * FROM blacklist WHERE id_card = %s", (id_card,))
            blacklist_entry = cursor.fetchone()
            
            if blacklist_entry:
                return {
                    "overdue_count": 99,
                    "loan_count": 99,
                    "is_blacklist": True,
                    "real_monthly_income": 0,
                    "id_card_province": self._parse_id_card_province(id_card),
                    "recent_query_count": 99,
                    "source": blacklist_entry.get("source", "UNKNOWN")
                }
            
            cursor.execute("SELECT * FROM user_external_features WHERE id_card = %s", (id_card,))
            features_entry = cursor.fetchone()
            
            if features_entry:
                return {
                    "overdue_count": features_entry.get("overdue_count_12m", 0),
                    "loan_count": features_entry.get("multi_head_loan_count", 0),
                    "is_blacklist": False,
                    "real_monthly_income": features_entry.get("credit_score", 500) * 30,
                    "id_card_province": self._parse_id_card_province(id_card),
                    "recent_query_count": features_entry.get("credit_query_count_3m", 0),
                    "device_is_virtual": features_entry.get("device_is_virtual", 0),
                    "ip_is_proxy": features_entry.get("ip_is_proxy", 0)
                }
            
            return None
        finally:
            if conn.is_connected():
                cursor.close()
                conn.close()
    
    def _generate_mock_data(self, id_card: str) -> dict:
        seed = int(hashlib.md5(id_card.encode()).hexdigest()[:8], 16)
        rng = random.Random(seed)
        
        return {
            "overdue_count": rng.randint(0, 3),
            "loan_count": rng.randint(0, 5),
            "is_blacklist": False,
            "real_monthly_income": rng.randint(3000, 30000),
            "id_card_province": self._parse_id_card_province(id_card),
            "recent_query_count": rng.randint(0, 10),
            "device_is_virtual": 1 if rng.random() < 0.03 else 0,
            "ip_is_proxy": 1 if rng.random() < 0.05 else 0
        }
    
    def _parse_id_card_province(self, id_card: str) -> str:
        province_map = {
            "11": "北京市", "12": "天津市", "13": "河北省", "14": "山西省", "15": "内蒙古自治区",
            "21": "辽宁省", "22": "吉林省", "23": "黑龙江省",
            "31": "上海市", "32": "江苏省", "33": "浙江省", "34": "安徽省", "35": "福建省", 
            "36": "江西省", "37": "山东省",
            "41": "河南省", "42": "湖北省", "43": "湖南省", "44": "广东省", "45": "广西壮族自治区", 
            "46": "海南省",
            "50": "重庆市", "51": "四川省", "52": "贵州省", "53": "云南省", "54": "西藏自治区",
            "61": "陕西省", "62": "甘肃省", "63": "青海省", "64": "宁夏回族自治区", "65": "新疆维吾尔自治区"
        }
        if len(id_card) >= 2:
            return province_map.get(id_card[:2], "未知")
        return "未知"
    
    def sync_credit_data(self, id_card: str, data: dict):
        if not self.db_available:
            print("数据库不可用，无法同步数据")
            return
        
        conn = self._get_db_connection()
        if not conn:
            return
        
        try:
            cursor = conn.cursor()
            
            cursor.execute(
                """
                INSERT INTO user_external_features (
                    id_card, credit_score, overdue_count_12m, credit_query_count_3m,
                    multi_head_loan_count, device_is_virtual, ip_is_proxy, data_source
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    credit_score = VALUES(credit_score),
                    overdue_count_12m = VALUES(overdue_count_12m),
                    credit_query_count_3m = VALUES(credit_query_count_3m),
                    multi_head_loan_count = VALUES(multi_head_loan_count),
                    device_is_virtual = VALUES(device_is_virtual),
                    ip_is_proxy = VALUES(ip_is_proxy)
                """,
                (
                    id_card,
                    data.get("real_monthly_income", 5000) // 30,
                    data.get("overdue_count", 0),
                    data.get("recent_query_count", 0),
                    data.get("loan_count", 0),
                    data.get("device_is_virtual", 0),
                    data.get("ip_is_proxy", 0),
                    "SYNC"
                )
            )
            
            conn.commit()
            print(f"征信数据同步成功: {id_card}")
        except Exception as e:
            print(f"数据同步失败: {str(e)}")
            conn.rollback()
        finally:
            if conn.is_connected():
                cursor.close()
                conn.close()