-- 孙七第三方特征：790–820 分档（非赵六满配），id_card 不变
-- 配合 L2 黑名单 seed：sql/seed_blacklist_sunqi_l2.sql

USE credit_data_db;

UPDATE user_external_features
SET
    days_employed = -2205,
    employment_years = 6.0,
    amt_income_total = 210000.0,
    credit_bureau_week = 0,
    credit_bureau_mon = 0,
    days_last_phone_change = 1058,
    active_loans_count = 1,
    ext_source_2 = 0.6669,
    ext_source_3 = 0.6754,
    flag_own_car = 1,
    gender_male = 0,
    married = 1,
    own_realty = 1,
    employment_stable = 1,
    credit_income_ratio = 3.5,
    cc_utilization = 0,
    loan_overdue_max_6m = 13,
    occupation_type = 'Core staff',
    education_type = '本科',
    target = 0,
    prev_refused_count = 30,
    updated_at = NOW()
WHERE id_card = '110112198702150007';
