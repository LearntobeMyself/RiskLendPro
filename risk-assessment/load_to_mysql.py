import pandas as pd
import mysql.connector
from mysql.connector import Error
from dotenv import load_dotenv
import os
import json

load_dotenv()

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", 3306)),
    "user": os.getenv("DB_USER", "admin"),
    "password": os.getenv("DB_PASSWORD", "admin"),
    "database": os.getenv("DB_NAME", "credit_data_db")
}

def create_database_if_not_exists():
    try:
        conn = mysql.connector.connect(
            host=DB_CONFIG["host"],
            port=DB_CONFIG["port"],
            user=DB_CONFIG["user"],
            password=DB_CONFIG["password"]
        )
        
        if conn.is_connected():
            cursor = conn.cursor()
            cursor.execute(f"CREATE DATABASE IF NOT EXISTS {DB_CONFIG['database']}")
            print(f"数据库 {DB_CONFIG['database']} 检查/创建完成")
            cursor.close()
            conn.close()
    except Error as e:
        print(f"创建数据库失败: {e}")

def create_tables_if_not_exists():
    conn = None
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        create_blacklist_table = """
            CREATE TABLE IF NOT EXISTS blacklist (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                id_card VARCHAR(18) NOT NULL COMMENT '身份证号',
                phone VARCHAR(20) COMMENT '手机号',
                reason VARCHAR(200) NOT NULL COMMENT '拉黑原因',
                source VARCHAR(50) NOT NULL COMMENT '来源',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
                expire_at DATETIME NULL COMMENT '过期时间',
                UNIQUE KEY uk_id_card (id_card)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单表';
        """
        cursor.execute(create_blacklist_table)
        
        create_features_table = """
            CREATE TABLE IF NOT EXISTS user_external_features (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                id_card VARCHAR(18) NOT NULL COMMENT '身份证号',
                credit_score INT COMMENT '央行征信分',
                overdue_count_12m INT DEFAULT 0 COMMENT '近12月逾期次数',
                credit_query_count_3m INT DEFAULT 0 COMMENT '近3月征信查询次数',
                multi_head_loan_count INT DEFAULT 0 COMMENT '多头借贷平台数',
                multi_head_loan_total_amount DECIMAL(15,2) DEFAULT 0 COMMENT '多头借贷总金额',
                device_is_virtual TINYINT(1) DEFAULT 0 COMMENT '是否虚拟设备',
                device_change_count_30d INT DEFAULT 0 COMMENT '近30天更换设备次数',
                ip_is_proxy TINYINT(1) DEFAULT 0 COMMENT '是否代理IP',
                data_source VARCHAR(50) COMMENT '数据来源',
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_id_card (id_card)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户外部特征表';
        """
        cursor.execute(create_features_table)
        
        create_rules_table = """
            CREATE TABLE IF NOT EXISTS scoring_rules (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                version VARCHAR(20) NOT NULL COMMENT '规则版本号',
                rule_content JSON NOT NULL COMMENT '特征权重JSON',
                intercept DECIMAL(8,4) NOT NULL COMMENT '模型截距',
                threshold_auto_approve DECIMAL(5,2) NOT NULL COMMENT '自动通过阈值',
                threshold_manual_review DECIMAL(5,2) NOT NULL COMMENT '人工审核阈值',
                is_active TINYINT(1) DEFAULT 0 COMMENT '是否激活',
                trained_at DATETIME COMMENT '训练时间',
                training_data_count INT COMMENT '训练数据量',
                accuracy DECIMAL(5,4) COMMENT '模型准确率',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_version (version),
                KEY idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评分规则配置表';
        """
        cursor.execute(create_rules_table)
        
        conn.commit()
        print("所有表检查/创建完成")
        
    except Error as e:
        print(f"创建表失败: {e}")
        if conn:
            conn.rollback()
    finally:
        if conn and conn.is_connected():
            cursor.close()
            conn.close()

def get_db_connection():
    return mysql.connector.connect(**DB_CONFIG)

def load_blacklist_to_mysql():
    df = pd.read_csv("data/cleaned/cleaned_blacklist.csv", encoding="utf-8-sig")
    
    conn = get_db_connection()
    cursor = conn.cursor()
    
    try:
        for _, row in df.iterrows():
            sql = """
                INSERT INTO blacklist (id_card, phone, reason, source, created_at, expire_at)
                VALUES (%s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                    phone = VALUES(phone),
                    reason = VALUES(reason),
                    source = VALUES(source),
                    expire_at = VALUES(expire_at)
            """
            expire_at = None if pd.isna(row["expire_at"]) else row["expire_at"]
            cursor.execute(sql, (
                row["id_card"],
                row["phone"],
                row["reason"],
                row["source"],
                row["created_at"],
                expire_at
            ))
        
        conn.commit()
        print(f"成功写入黑名单数据: {len(df)} 条")
    
    except Exception as e:
        print(f"写入黑名单数据失败: {e}")
        conn.rollback()
    finally:
        cursor.close()
        conn.close()

BATCH_SIZE = 100
MAX_RETRIES = 3

def get_db_connection(retry_count=0):
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        return conn
    except Error as e:
        if retry_count < MAX_RETRIES:
            print(f"连接数据库失败，重试 {retry_count + 1}/{MAX_RETRIES}...")
            return get_db_connection(retry_count + 1)
        else:
            raise e

def load_user_features_to_mysql():
    df = pd.read_csv("data/cleaned/cleaned_user_features.csv", encoding="utf-8-sig")
    
    conn = None
    cursor = None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        total_rows = len(df)
        inserted_rows = 0
        
        for i in range(0, total_rows, BATCH_SIZE):
            batch = df.iloc[i:i+BATCH_SIZE]
            for _, row in batch.iterrows():
                sql = """
                    INSERT INTO user_external_features (
                        id_card, credit_score, overdue_count_12m, 
                        credit_query_count_3m, multi_head_loan_count,
                        multi_head_loan_total_amount, device_is_virtual,
                        device_change_count_30d, ip_is_proxy,
                        data_source, updated_at
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                        credit_score = VALUES(credit_score),
                        overdue_count_12m = VALUES(overdue_count_12m),
                        credit_query_count_3m = VALUES(credit_query_count_3m),
                        multi_head_loan_count = VALUES(multi_head_loan_count),
                        multi_head_loan_total_amount = VALUES(multi_head_loan_total_amount),
                        device_is_virtual = VALUES(device_is_virtual),
                        device_change_count_30d = VALUES(device_change_count_30d),
                        ip_is_proxy = VALUES(ip_is_proxy),
                        data_source = VALUES(data_source),
                        updated_at = VALUES(updated_at)
                """
                cursor.execute(sql, (
                    row["id_card"],
                    row["credit_score"],
                    row["overdue_count_12m"],
                    row["credit_query_count_3m"],
                    row["multi_head_loan_count"],
                    row["multi_head_loan_total_amount"],
                    row["device_is_virtual"],
                    row["device_change_count_30d"],
                    row["ip_is_proxy"],
                    row["data_source"],
                    row["updated_at"]
                ))
            
            conn.commit()
            inserted_rows += len(batch)
            print(f"已写入 {inserted_rows}/{total_rows} 条用户特征数据...")
        
        print(f"成功写入用户特征数据: {total_rows} 条")
    
    except Exception as e:
        print(f"写入用户特征数据失败: {e}")
        if conn:
            try:
                conn.rollback()
            except:
                pass
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()

def load_scoring_rules_to_mysql():
    """加载评分规则到MySQL"""
    conn = None
    cursor = None
    try:
        with open("output/scoring_rules.json", "r", encoding="utf-8") as f:
            rules = json.load(f)
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        sql = """
            INSERT INTO scoring_rules (
                version, rule_content, intercept, 
                threshold_auto_approve, threshold_manual_review,
                is_active, trained_at, training_data_count, accuracy
            ) VALUES (%s, %s, %s, %s, %s, %s, NOW(), %s, %s)
            ON DUPLICATE KEY UPDATE
                rule_content = VALUES(rule_content),
                intercept = VALUES(intercept),
                threshold_auto_approve = VALUES(threshold_auto_approve),
                threshold_manual_review = VALUES(threshold_manual_review),
                is_active = VALUES(is_active),
                trained_at = VALUES(trained_at),
                training_data_count = VALUES(training_data_count),
                accuracy = VALUES(accuracy)
        """
        cursor.execute(sql, (
            rules.get("version", "v1.0"),
            json.dumps(rules, ensure_ascii=False),
            rules.get("intercept", 0),
            rules.get("thresholds", {}).get("auto_approve", 80),
            rules.get("thresholds", {}).get("manual_review", 60),
            1,
            rules.get("training_data", {}).get("total_count", 0),
            rules.get("model_metrics", {}).get("accuracy", 0)
        ))
        
        conn.commit()
        print("成功写入评分规则数据")
        
    except FileNotFoundError:
        print("警告：未找到评分规则文件 output/scoring_rules.json")
        print("提示：运行 python train_scoring_model.py 生成评分规则")
    except Exception as e:
        print(f"写入评分规则失败: {e}")
        if conn:
            conn.rollback()
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()

if __name__ == "__main__":
    print("=" * 50)
    print("步骤1: 创建数据库（如果不存在）")
    create_database_if_not_exists()
    
    print("\n步骤2: 创建表（如果不存在）")
    create_tables_if_not_exists()
    
    print("\n步骤3: 写入黑名单数据")
    load_blacklist_to_mysql()
    
    print("\n步骤4: 写入用户特征数据")
    load_user_features_to_mysql()
    
    print("\n步骤5: 写入评分规则数据")
    load_scoring_rules_to_mysql()
    
    print("\n" + "=" * 50)
    print("所有数据写入完成！")