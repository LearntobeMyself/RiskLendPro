import pandas as pd
import mysql.connector
from mysql.connector import Error
from dotenv import load_dotenv
import os
import json
from datetime import datetime

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
        
        cursor.execute("DROP TABLE IF EXISTS blacklist")
        create_blacklist_table = """
            CREATE TABLE blacklist (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                name VARCHAR(100) NOT NULL COMMENT '被执行人姓名/名称（匹配第一维度）',
                area_code VARCHAR(20) COMMENT '地区编码（由执行法院转换，匹配第二维度）',
                birth_year INT COMMENT '出生年份（从出生日期提取，匹配第三维度）',
                case_no VARCHAR(50) COMMENT '案号（人工审批时核对具体案件）',
                court_name VARCHAR(100) COMMENT '执行法院（辅助展示）',
                duty_status VARCHAR(50) COMMENT '被执行人履行情况（判定风险严重程度）',
                behavior_details VARCHAR(500) COMMENT '失信被执行人行为情况（具体原因）',
                risk_level VARCHAR(10) COMMENT '风险等级（HIGH/MEDIUM/LOW）',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
                expire_at DATETIME NULL COMMENT '过期时间（NULL=永久）',
                KEY idx_name (name),
                KEY idx_area_code (area_code),
                KEY idx_birth_year (birth_year),
                KEY idx_risk_level (risk_level)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='失信被执行人黑名单表';
        """
        cursor.execute(create_blacklist_table)
        print("创建黑名单表完成")
        
        cursor.execute("DROP TABLE IF EXISTS user_external_features")
        create_features_table = """
            CREATE TABLE user_external_features (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                sk_id_curr BIGINT NOT NULL COMMENT 'Home Credit 申请ID（SK_ID_CURR）',
                id_card VARCHAR(20) NULL COMMENT '演示用身份证号（与主库 user.id_card 一致，Java 按此关联）',
                days_birth INT DEFAULT 0 COMMENT '出生日期天数（负数，验真：核对年龄）',
                days_employed INT DEFAULT 0 COMMENT '入职天数（负数，验真：核对工作年限）',
                amt_income_total DECIMAL(15,2) DEFAULT 0 COMMENT '后台记录收入（验真：核实收入）',
                credit_bureau_week INT DEFAULT 0 COMMENT '近1周征信查询次数（评分：评估多头风险）',
                credit_bureau_mon INT DEFAULT 0 COMMENT '近1月征信查询次数（评分：评估多头风险）',
                days_last_phone_change INT DEFAULT 0 COMMENT '手机换号天数（评分：评估稳定性）',
                active_loans_count INT DEFAULT 0 COMMENT '活跃贷款数（评分/验真：负债水平）',
                ext_source_2 DECIMAL(10,6) DEFAULT 0 COMMENT '第三方评分A（评分：权重极高）',
                ext_source_3 DECIMAL(10,6) DEFAULT 0 COMMENT '第三方评分B（评分：补充权威评价）',
                flag_own_car TINYINT(1) DEFAULT 0 COMMENT '是否有车（0=否，1=是，验真：核实资产）',
                gender_male TINYINT(1) DEFAULT 0 COMMENT '性别男=1（WOE/回测）',
                married TINYINT(1) DEFAULT 0 COMMENT '已婚=1（WOE/回测）',
                own_realty TINYINT(1) DEFAULT 0 COMMENT '有房=1（WOE/回测）',
                employment_stable TINYINT(1) DEFAULT 0 COMMENT '稳定就业=1（WOE）',
                credit_income_ratio DECIMAL(12,4) DEFAULT 0 COMMENT '授信收入比（WOE）',
                cc_utilization DECIMAL(12,4) DEFAULT 0 COMMENT '信用卡使用代理（WOE）',
                loan_overdue_max_6m INT DEFAULT 0 COMMENT '逾期次数代理（WOE）',
                occupation_type VARCHAR(50) COMMENT '职业类型（评分：职业风险分级）',
                education_type VARCHAR(50) COMMENT '学历（验真：核实背景）',
                target TINYINT(1) DEFAULT 0 COMMENT '历史标签（0=正常，1=逾期，回测：验证模型）',
                prev_refused_count INT DEFAULT 0 COMMENT '历史被拒次数（拦截：严重风险则拒绝）',
                data_source VARCHAR(50) COMMENT '数据来源',
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间',
                UNIQUE KEY uk_sk_id_curr (sk_id_curr),
                KEY idx_id_card (id_card),
                KEY idx_days_birth (days_birth),
                KEY idx_target (target),
                KEY idx_prev_refused (prev_refused_count)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户外部行为特征表（来自Home Credit数据）';
        """
        cursor.execute(create_features_table)
        print("创建用户特征表完成")

        cursor.execute("DROP TABLE IF EXISTS scoring_rules")
        create_rules_table = """
            CREATE TABLE scoring_rules (
                id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                version VARCHAR(20) NOT NULL COMMENT '规则版本号',
                rule_content JSON NOT NULL COMMENT '完整规则JSON备份',
                feature_weights JSON NOT NULL COMMENT 'LR特征权重 rules.feature_weights',
                scorecard JSON NOT NULL COMMENT '评分卡规则 rules.scorecard',
                application_rule_bonus JSON NULL COMMENT '申请表策略加成规则',
                feature_scores JSON NULL COMMENT '旧版逐项评分规则',
                feature_derivation JSON NULL COMMENT '特征推导说明',
                intercept DECIMAL(16,8) NOT NULL COMMENT '逻辑回归截距项',
                threshold_auto_approve DECIMAL(10,2) NOT NULL COMMENT '自动通过阈值（PDO量表）',
                threshold_manual_review DECIMAL(10,2) NOT NULL COMMENT '人工审核阈值（PDO量表）',
                is_active TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否激活（0=否，1=是）',
                trained_at DATETIME NULL COMMENT '模型训练时间',
                training_data_count INT NULL COMMENT '训练数据量',
                accuracy DECIMAL(10,6) NULL COMMENT '模型准确率',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
                PRIMARY KEY (id),
                UNIQUE KEY uk_version (version),
                KEY idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控评分规则表——解析列+全量JSON';
        """
        cursor.execute(create_rules_table)
        print("创建评分规则表完成")
        
        conn.commit()
        print("所有表创建完成")
        
    except Error as e:
        print(f"创建表失败: {e}")
        if conn:
            conn.rollback()
    finally:
        if conn and conn.is_connected():
            cursor.close()
            conn.close()

def get_db_connection(retry_count=0, max_retries=3):
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        return conn
    except Error as e:
        if retry_count < max_retries:
            print(f"连接数据库失败，重试 {retry_count + 1}/{max_retries}...")
            return get_db_connection(retry_count + 1, max_retries)
        else:
            raise e

def load_blacklist_to_mysql():
    df = pd.read_csv("data/cleaned/cleaned_blacklist.csv", encoding="utf-8-sig")
    
    conn = None
    cursor = None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        sql = """
            INSERT INTO blacklist (
                name, area_code, birth_year, case_no, 
                court_name, duty_status, behavior_details, 
                risk_level, created_at, expire_at
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        for _, row in df.iterrows():
            expire_at = None if pd.isna(row["expire_at"]) else row["expire_at"]
            cursor.execute(sql, (
                None if pd.isna(row["name"]) else row["name"],
                None if pd.isna(row["area_code"]) else row["area_code"],
                None if pd.isna(row["birth_year"]) else row["birth_year"],
                None if pd.isna(row["case_no"]) else row["case_no"],
                None if pd.isna(row["court_name"]) else row["court_name"],
                None if pd.isna(row["duty_status"]) else row["duty_status"],
                None if pd.isna(row["behavior_details"]) else row["behavior_details"],
                None if pd.isna(row["risk_level"]) else row["risk_level"],
                None if pd.isna(row["created_at"]) else row["created_at"],
                expire_at
            ))
        
        conn.commit()
        print(f"成功写入黑名单数据: {len(df)} 条")
    
    except Exception as e:
        print(f"写入黑名单数据失败: {e}")
        if conn:
            conn.rollback()
    finally:
        if cursor:
            cursor.close()
        if conn and conn.is_connected():
            conn.close()

BATCH_SIZE = 100

def load_user_features_to_mysql():
    df = pd.read_csv("data/cleaned/cleaned_user_features.csv", encoding="utf-8-sig")
    
    conn = None
    cursor = None
    
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        total_rows = len(df)
        inserted_rows = 0
        
        sql = """
            INSERT INTO user_external_features (
                sk_id_curr, id_card, days_birth, days_employed, amt_income_total,
                credit_bureau_week, credit_bureau_mon, days_last_phone_change,
                active_loans_count, ext_source_2, ext_source_3,
                flag_own_car, gender_male, married, own_realty, employment_stable,
                credit_income_ratio, cc_utilization, loan_overdue_max_6m,
                occupation_type, education_type,
                target, prev_refused_count, data_source, updated_at
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        for i in range(0, total_rows, BATCH_SIZE):
            batch = df.iloc[i:i+BATCH_SIZE]
            for _, row in batch.iterrows():
                days_birth = int(row["days_birth"]) if "days_birth" in row else -int(row["age"]) * 365
                days_employed = int(row["days_employed"]) if "days_employed" in row else -int(row["employment_years"] * 365)
                sk_id = int(row["sk_id_curr"]) if "sk_id_curr" in row and pd.notna(row["sk_id_curr"]) else 0
                id_card_val = str(row["id_card"]) if "id_card" in row and pd.notna(row["id_card"]) else None
                cursor.execute(sql, (
                    sk_id,
                    id_card_val,
                    days_birth,
                    days_employed,
                    float(row["AMT_INCOME_TOTAL"]),
                    int(row["credit_query_week"]),
                    int(row["credit_query_month"]),
                    int(row["phone_change_days"]),
                    int(row["active_loans_count"]),
                    float(row["ext_source_2"]),
                    float(row["ext_source_3"]),
                    int(row["has_car"]),
                    int(row.get("gender_male", 0)),
                    int(row.get("married", 0)),
                    int(row.get("own_realty", 0)),
                    int(row.get("employment_stable", 0)),
                    float(row.get("credit_income_ratio", 0)),
                    float(row.get("cc_utilization", 0)),
                    int(row.get("loan_overdue_max_6m", 0)),
                    row["occupation_type"],
                    row["education"],
                    int(row["has_default_history"]),
                    int(row["prev_refused_count"]),
                    "Home Credit",
                    datetime.now().strftime("%Y-%m-%d %H:%M:%S")
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

SAMPLE_EXTERNAL_FEATURES_ROWS = [
    ("110101198503151001", 780, 0, 1, 1, 5000.00, 0, 0, 0, "SAMPLE_TRAIN_MODEL_DOC"),
    ("110101198503151002", 680, 0, 6, 5, 45000.00, 0, 1, 0, "SAMPLE_TRAIN_MODEL_DOC"),
    ("110101198503151003", 520, 4, 15, 12, 280000.00, 0, 2, 0, "SAMPLE_TRAIN_MODEL_DOC"),
    ("110101198503151004", 650, 0, 2, 2, 12000.00, 1, 3, 1, "SAMPLE_TRAIN_MODEL_DOC"),
]

def build_sample_external_features_sql():
    lines = ["-- Persona A–D：与 load_sample_external_features_to_mysql() 写入内容一致"]
    for row in SAMPLE_EXTERNAL_FEATURES_ROWS:
        lines.append(
            "INSERT INTO user_external_features (\n"
            "    id_card, credit_score, overdue_count_12m, credit_query_count_3m,\n"
            "    multi_head_loan_count, multi_head_loan_total_amount,\n"
            "    device_is_virtual, device_change_count_30d, ip_is_proxy, data_source\n"
            ") VALUES (\n"
            f"    '{row[0]}', {row[1]}, {row[2]}, {row[3]},\n"
            f"    {row[4]}, {row[5]:.2f},\n"
            f"    {row[6]}, {row[7]}, {row[8]}, '{row[9]}'\n"
            ");"
        )
    return "\n\n".join(lines)

def load_sample_external_features_to_mysql():
    print("警告：示例数据需要与新表结构匹配，当前跳过此步骤")

def load_scoring_rules_to_mysql():
    conn = None
    cursor = None
    try:
        with open("output/scoring_rules.json", "r", encoding="utf-8") as f:
            rules = json.load(f)
        
        conn = get_db_connection()
        cursor = conn.cursor()
        
        version = rules.get("version", "v1.0")
        
        cursor.execute("DELETE FROM scoring_rules WHERE version = %s", (version,))
        
        fw = rules.get("feature_weights") or rules.get("coefficients") or {}
        sc = rules.get("scorecard") or {}
        arb = rules.get("application_rule_bonus")
        fscores = rules.get("feature_scores")
        fderiv = rules.get("feature_derivation")

        sql = """
            INSERT INTO scoring_rules (
                version, rule_content, feature_weights, scorecard,
                application_rule_bonus, feature_scores, feature_derivation,
                intercept, threshold_auto_approve, threshold_manual_review,
                is_active, trained_at, training_data_count, accuracy
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), %s, %s)
        """
        cursor.execute(sql, (
            version,
            json.dumps(rules, ensure_ascii=False),
            json.dumps(fw, ensure_ascii=False),
            json.dumps(sc, ensure_ascii=False),
            json.dumps(arb, ensure_ascii=False) if arb is not None else None,
            json.dumps(fscores, ensure_ascii=False) if fscores is not None else None,
            json.dumps(fderiv, ensure_ascii=False) if fderiv is not None else None,
            rules.get("intercept", 0),
            rules.get("thresholds", {}).get("auto_approve", 720),
            rules.get("thresholds", {}).get("manual_review", 580),
            1,
            rules.get("training_data", {}).get("total_count", 0),
            rules.get("model_metrics", {}).get("accuracy", 0),
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


def create_b_card_tables_if_not_exists():
    conn = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS user_behavior_features (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                sk_id_curr BIGINT NOT NULL,
                id_card VARCHAR(20) NULL,
                feature_json JSON NOT NULL,
                data_source VARCHAR(50) DEFAULT 'Home Credit B-card',
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_sk_id_curr (sk_id_curr),
                KEY idx_id_card (id_card)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """
        )
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS behavior_scoring_rules (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                version VARCHAR(32) NOT NULL,
                rule_content JSON NOT NULL,
                feature_weights JSON NOT NULL,
                scorecard JSON NOT NULL,
                intercept DECIMAL(16,8) NOT NULL,
                threshold_watch DECIMAL(10,2) NOT NULL,
                threshold_reduce_limit DECIMAL(10,2) NOT NULL,
                is_active TINYINT(1) NOT NULL DEFAULT 1,
                trained_at DATETIME NULL,
                training_data_count INT NULL,
                accuracy DECIMAL(10,6) NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_version (version)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """
        )
        conn.commit()
        print("B 卡表检查/创建完成")
        cursor.close()
        conn.close()
    except Exception as e:
        print(f"创建 B 卡表失败: {e}")


def normalize_id_card(val):
    if val is None or (isinstance(val, float) and pd.isna(val)):
        return None
    s = str(val).strip()
    if not s or s.lower() == "nan":
        return None
    if "e" in s.lower():
        s = f"{int(float(s))}"
    elif s.endswith(".0"):
        s = s[:-2]
    return s


def load_b_card_to_mysql():
    create_b_card_tables_if_not_exists()
    rules_path = "output/b_scoring_rules.json"
    csv_path = "data/cleaned/cleaned_behavior_features.csv"

    if os.path.isfile(rules_path):
        try:
            with open(rules_path, "r", encoding="utf-8") as f:
                rules = json.load(f)
            conn = get_db_connection()
            cursor = conn.cursor()
            version = rules.get("version", "v1.0-b-woe")
            th = rules.get("thresholds") or {}
            fw = rules.get("coefficients") or {}
            sc = rules.get("scorecard") or {}
            metrics = rules.get("model_metrics") or {}
            train = rules.get("training_data") or {}
            cursor.execute("DELETE FROM behavior_scoring_rules WHERE version = %s", (version,))
            cursor.execute(
                """
                INSERT INTO behavior_scoring_rules (
                    version, rule_content, feature_weights, scorecard,
                    intercept, threshold_watch, threshold_reduce_limit,
                    is_active, trained_at, training_data_count, accuracy
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, 1, NOW(), %s, %s)
                """,
                (
                    version,
                    json.dumps(rules, ensure_ascii=False),
                    json.dumps(fw, ensure_ascii=False),
                    json.dumps(sc, ensure_ascii=False),
                    float(rules.get("intercept", 0)),
                    float(th.get("watch", 650)),
                    float(th.get("reduce_limit", 550)),
                    int(train.get("total_count", 0)),
                    float(metrics.get("accuracy", 0)),
                ),
            )
            conn.commit()
            print(f"B 卡规则已写入: {version}")
            cursor.close()
            conn.close()
        except Exception as e:
            print(f"写入 B 卡规则失败: {e}")
    else:
        print("警告：未找到 output/b_scoring_rules.json")

    if not os.path.isfile(csv_path):
        print(f"警告：未找到 {csv_path}，跳过 B 卡特征（可先运行 clean_behavior_features.py）")
        return

    try:
        df = pd.read_csv(csv_path, encoding="utf-8-sig", dtype={"id_card": str})
        feature_cols = [c for c in df.columns if c not in ("sk_id_curr", "id_card")]
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("DELETE FROM user_behavior_features")
        for _, row in df.iterrows():
            feat = {c: float(row[c]) if pd.notna(row[c]) else 0.0 for c in feature_cols}
            cursor.execute(
                """
                INSERT INTO user_behavior_features (sk_id_curr, id_card, feature_json, updated_at)
                VALUES (%s, %s, %s, %s)
                """,
                (
                    int(row["sk_id_curr"]),
                    normalize_id_card(row.get("id_card")),
                    json.dumps(feat, ensure_ascii=False),
                    datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                ),
            )
        conn.commit()
        print(f"成功写入 B 卡行为特征: {len(df)} 条")
        cursor.close()
        conn.close()
    except Exception as e:
        print(f"写入 B 卡行为特征失败: {e}")


if __name__ == "__main__":
    print("=" * 60)
    print("步骤1: 创建数据库（如果不存在）")
    create_database_if_not_exists()
    
    print("\n步骤2: 创建表（删除旧表并创建新表）")
    create_tables_if_not_exists()
    
    print("\n步骤3: 写入黑名单数据")
    load_blacklist_to_mysql()
    
    print("\n步骤4: 写入用户特征数据")
    load_user_features_to_mysql()
    
    print("\n步骤5: 写入评分规则数据")
    load_scoring_rules_to_mysql()

    print("\n步骤6: 写入 B 卡行为特征与规则（不影响 A 卡）")
    load_b_card_to_mysql()
    
    print("\n" + "=" * 60)
    print("所有数据写入完成！")