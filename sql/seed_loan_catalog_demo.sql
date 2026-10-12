-- 演示产品目录种子：4 家机构 + 12 条产品（11 上架 / 1 下架）
-- 可重复执行：按机构名、产品名、object_key 去重。
-- 分类和标签需已存在（loan.schema.auto-migrate-catalog 或 migration_loan_catalog.sql）。
-- 先跑 python docs/seed-catalog/sync_to_cos.py，把图片 PUT 到 COS；本脚本只登记 object_key。
-- 运行时封面 URL 由 CosStorageService.accessUrl(object_key) 签发，不读仓库里的本地文件。

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 机构
-- ---------------------------------------------------------------------------
INSERT INTO loan_institution (institution_name, institution_type, logo_key, introduction, enabled, deleted, version)
SELECT '星河银行', 'BANK', 'loan/institution_logo/seed/logo-xinghe-bank.jpg',
       '全国性股份制银行演示品牌。提供工薪信用贷、公积金贷、公职优待贷和住房抵押贷款。非真实持牌机构。',
       1, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM loan_institution WHERE institution_name = '星河银行' AND deleted = 0);

INSERT INTO loan_institution (institution_name, institution_type, logo_key, introduction, enabled, deleted, version)
SELECT '蓝海消费金融', 'CONSUMER_FINANCE', 'loan/institution_logo/seed/logo-lanhai-cf.jpg',
       '持牌消费金融演示品牌。提供循环额度信用贷，以及装修、教育、数码等消费分期。非真实持牌机构。',
       1, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM loan_institution WHERE institution_name = '蓝海消费金融' AND deleted = 0);

INSERT INTO loan_institution (institution_name, institution_type, logo_key, introduction, enabled, deleted, version)
SELECT '青梧小贷', 'MICRO_LOAN', 'loan/institution_logo/seed/logo-qingwu-micro.jpg',
       '区域小额贷款演示品牌。服务小微企业主和个体工商户的经营周转。非真实持牌机构。',
       1, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM loan_institution WHERE institution_name = '青梧小贷' AND deleted = 0);

INSERT INTO loan_institution (institution_name, institution_type, logo_key, introduction, enabled, deleted, version)
SELECT '途安汽车金融', 'AUTO_FINANCE', 'loan/institution_logo/seed/logo-tuan-auto.jpg',
       '汽车金融演示品牌。以本人名下非营运小型汽车做抵押提供周转贷款。非真实持牌机构。',
       1, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM loan_institution WHERE institution_name = '途安汽车金融' AND deleted = 0);

-- ---------------------------------------------------------------------------
-- 产品（ever_on_shelf=1 才会进入知识库导出）
-- ---------------------------------------------------------------------------
INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '薪金闪贷', i.id, 'PERSONAL_CREDIT',
  '面向有稳定工资流水的工薪族，纯信用、无抵押。小额起步、线上审批，适合发薪日前的短期缺口。',
  'ON_SHELF', 1,
  1000, 200000, 3, 24, 'MONTH',
  7.20, 18.25, 'SIMPLE', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT","INTEREST_FIRST"]' AS JSON), '最快当日到账',
  CAST('["SALARIED"]' AS JSON), 22, 55,
  '月收入不低于4000元或近6个月工资流水可核验',
  '近2年无连三累六，当前无严重逾期',
  '企事业单位或连续缴纳社保的劳动合同员工',
  'NONE', CAST('["全国"]' AS JSON), NULL,
  CAST('["身份证","借记卡","近6个月工资流水"]' AS JSON),
  'NONE', NULL, '支持提前结清，按实际占用天数计息，不收违约金',
  '演示产品。实际利率按个人资质定价，最终以审批和合同为准。',
  '星河银行官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 100, 0, 0
FROM loan_institution i
WHERE i.institution_name = '星河银行' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '薪金闪贷' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '随心周转金', i.id, 'PERSONAL_CREDIT',
  '循环额度信用贷。额度内可反复支用，按日计息，适合几天到一两个月的周转，不适合长期大额经营用途。',
  'ON_SHELF', 1,
  500, 200000, 1, 12, 'MONTH',
  5.50, 21.90, 'SIMPLE', 'ALL_IN',
  CAST('["FLEXIBLE","INTEREST_FIRST","EQUAL_INSTALLMENT"]' AS JSON), '审批通过后即时到账',
  CAST('["SALARIED"]' AS JSON), 22, 55,
  '有稳定收入来源即可申请，实际以审批为准',
  '近6个月逾期不超过1次',
  '职业不限但需可联系',
  'NONE', CAST('["全国"]' AS JSON), NULL,
  CAST('["身份证","本人银行卡"]' AS JSON),
  'NONE', NULL, '随时还款，利息计至还款日前一天，不收提前还款违约金',
  '演示产品。循环额度可能随使用情况和征信变化调升或调降。',
  '蓝海消费金融官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 95, 0, 0
FROM loan_institution i
WHERE i.institution_name = '蓝海消费金融' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '随心周转金' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '公积金专享贷', i.id, 'PERSONAL_CREDIT',
  '连续缴存住房公积金的在职人员专享。银行表内信用贷，利率低于普通消金产品，审核看缴存连续性和账户状态。',
  'ON_SHELF', 1,
  10000, 500000, 6, 60, 'MONTH',
  3.85, 8.80, 'SIMPLE', 'INTEREST_ONLY',
  CAST('["EQUAL_INSTALLMENT","EQUAL_PRINCIPAL"]' AS JSON), '审批通过后1至3个工作日',
  CAST('["PROVIDENT_FUND"]' AS JSON), 23, 55,
  '连续缴存公积金满6个月且账户状态正常',
  '征信良好，无当前逾期',
  '缴存单位在职',
  'NONE', CAST('["全国"]' AS JSON), NULL,
  CAST('["身份证","公积金缴存证明","收入证明"]' AS JSON),
  'HAS', '部分地区可能按当地政策收取担保或保险费用，以合同为准',
  '贷款期满6个月后可提前结清，按已计利息收取至结清日',
  '演示产品。缴存中断、提取后余额过低可能影响额度。',
  '星河银行官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 90, 0, 0
FROM loan_institution i
WHERE i.institution_name = '星河银行' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '公积金专享贷' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '公职优待贷', i.id, 'PERSONAL_CREDIT',
  '公务员、事业单位在编及国有企业正式员工专享。客群稳定，利率下限接近银行优质零售定价。',
  'ON_SHELF', 1,
  20000, 800000, 12, 60, 'MONTH',
  3.45, 6.90, 'SIMPLE', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT","EQUAL_PRINCIPAL"]' AS JSON), '审批通过后1至2个工作日',
  CAST('["CIVIL_SERVANT"]' AS JSON), 22, 58,
  '财政统发或单位代发工资',
  '近5年无贷款核销和严重逾期',
  '公务员、事业单位在编或国有企业正式员工',
  'NONE', CAST('["全国"]' AS JSON), NULL,
  CAST('["身份证","工作证或在职证明","近6个月工资流水"]' AS JSON),
  'NONE', NULL, '允许部分提前还款，无违约金',
  '演示产品。劳务派遣、临聘人员不适用本产品。',
  '星河银行官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 85, 0, 0
FROM loan_institution i
WHERE i.institution_name = '星河银行' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '公职优待贷' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '经营周转贷', i.id, 'BUSINESS',
  '给小微企业主做进货、发工资、淡旺季周转。纯信用，不强制抵押。资金不得用于购房、理财和股本权益投资。',
  'ON_SHELF', 1,
  10000, 1000000, 3, 36, 'MONTH',
  5.80, 12.80, 'SIMPLE', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT","INTEREST_FIRST"]' AS JSON), '资料齐后1至3个工作日',
  CAST('["SME_OWNER","SELF_EMPLOYED"]' AS JSON), 22, 60,
  '近12个月经营流水可覆盖拟借本息',
  '企业及企业主征信无当前逾期，近24个月无连三累六',
  '企业法定代表人或持股10%以上且持股满1年的股东',
  'OPTIONAL', CAST('["全国"]' AS JSON),
  CAST('{"minEstablishYears":1,"minOperatingMonths":12,"revenueRequirement":"近一年营业收入原则上不低于50万元","licenseRequirement":"需提供在营状态的营业执照"}' AS JSON),
  CAST('["身份证","营业执照","近12个月对公或个人经营流水"]' AS JSON),
  'NONE', NULL, '到期日前可申请无还本续贷，以机构审批为准',
  '演示产品。空壳公司、无真实经营痕迹无法通过。',
  '青梧小贷官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 80, 0, 0
FROM loan_institution i
WHERE i.institution_name = '青梧小贷' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '经营周转贷' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '个体商户快贷', i.id, 'BUSINESS',
  '个体工商户小额快贷。门槛低于企业主产品，额度更小、利率更高，适合档口、餐饮、便利店的进货周转。',
  'ON_SHELF', 1,
  5000, 300000, 3, 24, 'MONTH',
  8.80, 16.80, 'SIMPLE', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT","FLEXIBLE"]' AS JSON), '工作日24小时内',
  CAST('["SELF_EMPLOYED"]' AS JSON), 22, 55,
  '近6个月经营收款可核验',
  '当前无逾期',
  '持有有效个体工商户营业执照的经营者',
  'NONE', CAST('["全国"]' AS JSON),
  CAST('{"minEstablishYears":1,"minOperatingMonths":6,"revenueRequirement":"近6个月经营收款可核验","licenseRequirement":"个体工商户营业执照在有效期内"}' AS JSON),
  CAST('["身份证","营业执照","收款码或对账单"]' AS JSON),
  'NONE', NULL, '支持提前结清，按实际占用天数计息',
  '演示产品。注册地与实际经营地长期不一致可能被拒。',
  '青梧小贷官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 75, 0, 0
FROM loan_institution i
WHERE i.institution_name = '青梧小贷' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '个体商户快贷' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '安居房抵贷', i.id, 'MORTGAGE',
  '用本人或配偶名下住宅做抵押，额度高、年限长。资金用途按消费或经营在合同中约定，不得违规流入房地产和股市。',
  'ON_SHELF', 1,
  100000, 5000000, 12, 120, 'MONTH',
  3.85, 6.80, 'SIMPLE', 'INTEREST_ONLY',
  CAST('["EQUAL_INSTALLMENT","EQUAL_PRINCIPAL"]' AS JSON), '完成抵押登记后1至5个工作日',
  CAST('["PROPERTY_OWNER"]' AS JSON), 22, 60,
  '有稳定收入证明月供覆盖能力',
  '无当前逾期，近24个月贷款逾期不超过2次',
  '不限职业，需能提供收入或经营材料',
  'REQUIRED', CAST('["全国"]' AS JSON),
  CAST('{"collateralTypes":["HOUSE"],"collateralValueRequirement":"住宅或公寓，房龄原则上不超过30年、产权清晰可办理抵押","maxLoanToValue":70}' AS JSON),
  CAST('["身份证","房产证","婚姻状况证明","收入证明"]' AS JSON),
  'HAS', '房产评估费、抵押登记费由借款人承担，以当地不动产中心及评估机构报价为准',
  '可提前结清，按已计利息收取至结清日',
  '演示产品。共有产权、未结清按揭需征得共有人或按揭行同意。',
  '星河银行官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 70, 0, 0
FROM loan_institution i
WHERE i.institution_name = '星河银行' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '安居房抵贷' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '途安车抵贷', i.id, 'MORTGAGE',
  '本人名下非营运小客车抵押，评估后放款。适合有车、短期需要一笔周转金但不想动房产的人。',
  'ON_SHELF', 1,
  10000, 500000, 6, 36, 'MONTH',
  7.80, 15.60, 'SIMPLE', 'INTEREST_ONLY',
  CAST('["EQUAL_INSTALLMENT"]' AS JSON), '车辆评估并办理抵押后1至2个工作日',
  CAST('["CAR_OWNER"]' AS JSON), 22, 55,
  '暂无刚性收入门槛，以车辆评估和征信综合核定',
  '当前无逾期',
  '不限职业',
  'REQUIRED', CAST('["全国"]' AS JSON),
  CAST('{"collateralTypes":["VEHICLE"],"collateralValueRequirement":"本人名下非营运小型汽车，车龄一般不超过8年","maxLoanToValue":80}' AS JSON),
  CAST('["身份证","行驶证","车辆登记证"]' AS JSON),
  'HAS', '车辆评估及抵押登记相关费用在放款前一次性收取或从额度中扣减，以合同为准',
  '允许提前结清，可能收取不超过剩余本金1%的手续费，以合同为准',
  '演示产品。营运车辆、抵押状态车辆、大事故车不收。',
  '途安汽车金融官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 65, 0, 0
FROM loan_institution i
WHERE i.institution_name = '途安汽车金融' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '途安车抵贷' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '安心家装分期', i.id, 'CONSUMER_INSTALLMENT',
  '装修场景分期。审批后款项直接付给合作装修公司或建材商户，不现金支用，降低挪用风险。',
  'ON_SHELF', 1,
  5000, 300000, 6, 36, 'MONTH',
  6.90, 15.80, 'IRR', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT"]' AS JSON), '商户确认开工或材料订单后分期划付',
  CAST('["PROPERTY_OWNER"]' AS JSON), 22, 55,
  '有装修合同或报价单',
  '近12个月逾期不超过1次',
  '不限职业',
  'NONE', CAST('["全国"]' AS JSON),
  CAST('{"scenes":["DECORATION"],"merchantScope":"平台合作装修公司及建材商户","paidToMerchant":true}' AS JSON),
  CAST('["身份证","装修合同","房产证明或租赁合同"]' AS JSON),
  'NONE', NULL, '装修验收前可申请提前结清',
  '演示产品。现金提现、支付给非合作商户不在本产品范围内。',
  '蓝海消费金融官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 60, 0, 0
FROM loan_institution i
WHERE i.institution_name = '蓝海消费金融' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '安心家装分期' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '学途教育分期', i.id, 'CONSUMER_INSTALLMENT',
  '学历提升、职业培训学费分期。按学期或按培训周期还款，学费付给合作机构。',
  'ON_SHELF', 1,
  3000, 150000, 6, 24, 'MONTH',
  5.90, 12.80, 'IRR', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT"]' AS JSON), '机构确认入学或开课日后划付',
  CAST('["GENERAL"]' AS JSON), 18, 45,
  '能承担对应学期或培训周期的还款',
  '无当前逾期',
  '在校或在职均可',
  'NONE', CAST('["全国"]' AS JSON),
  CAST('{"scenes":["EDUCATION"],"merchantScope":"平台合作培训及学历提升机构","paidToMerchant":true}' AS JSON),
  CAST('["身份证","录取通知或培训合同"]' AS JSON),
  'NONE', NULL, '退学退费按培训机构规则处理，已产生利息不退',
  '演示产品。兴趣类短期体验课可能不在合作范围内。',
  '蓝海消费金融官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 55, 0, 0
FROM loan_institution i
WHERE i.institution_name = '蓝海消费金融' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '学途教育分期' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '智选数码分期', i.id, 'CONSUMER_INSTALLMENT',
  '手机、电脑、数码配件购物分期。适合不想一次性付清3C的用户，额度小、期限短。',
  'ON_SHELF', 1,
  500, 30000, 3, 24, 'MONTH',
  7.20, 18.00, 'IRR', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT"]' AS JSON), '下单支付时以分期额度结算给商户',
  CAST('["GENERAL"]' AS JSON), 18, 50,
  '不设刚性收入门槛，以订单金额和征信综合核定',
  '近6个月无当前逾期',
  '不限职业',
  'NONE', CAST('["全国"]' AS JSON),
  CAST('{"scenes":["DIGITAL","SHOPPING"],"merchantScope":"合作数码卖场及线上旗舰店","paidToMerchant":true}' AS JSON),
  CAST('["身份证"]' AS JSON),
  'NONE', NULL, '收货7日内按商户规则退货后可撤销未入账分期',
  '演示产品。黄金、投资收藏类商品不可用本分期。',
  '蓝海消费金融官方App产品页（演示整理）', '2026-10-08', NULL, NOW(), 50, 0, 0
FROM loan_institution i
WHERE i.institution_name = '蓝海消费金融' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '智选数码分期' AND p.institution_id = i.id AND p.deleted = 0
  );

INSERT INTO loan_product (
  product_name, institution_id, category_code, summary, status, ever_on_shelf,
  min_amount, max_amount, min_term, max_term, term_unit,
  min_annual_rate, max_annual_rate, rate_calc_method, rate_scope, repayment_methods, disbursement_time,
  target_groups, min_age, max_age, income_requirement, credit_requirement, occupation_requirement,
  mortgage_required, regions, extra_conditions, materials,
  fee_status, fee_description, prepayment_description, remark,
  data_source, verified_at, last_off_shelf_time, knowledge_time, sort_weight, deleted, version
)
SELECT
  '暑期消费专项', i.id, 'CONSUMER_INSTALLMENT',
  '暑期购物活动额度，活动期结束后已下架。用户端不再展示，知识库仅保留下架说明。',
  'OFF_SHELF', 1,
  1000, 20000, 3, 12, 'MONTH',
  9.90, 18.00, 'IRR', 'ALL_IN',
  CAST('["EQUAL_INSTALLMENT"]' AS JSON), '活动期内下单后即时额度支付',
  CAST('["GENERAL"]' AS JSON), 18, 40,
  '活动期内有消费订单即可申请',
  '无当前逾期',
  '不限职业',
  'NONE', CAST('["全国"]' AS JSON),
  CAST('{"scenes":["SHOPPING"],"merchantScope":"活动指定合作商户","paidToMerchant":true}' AS JSON),
  CAST('["身份证"]' AS JSON),
  'NONE', NULL, '活动下架后未结清借据仍按原合同还款',
  '演示产品，已下架。如需了解现状请咨询蓝海消费金融官方渠道。',
  '蓝海消费金融官方App产品页（演示整理）', '2026-10-08', '2026-09-01 00:00:00', NOW(), 10, 0, 0
FROM loan_institution i
WHERE i.institution_name = '蓝海消费金融' AND i.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM loan_product p
    WHERE p.product_name = '暑期消费专项' AND p.institution_id = i.id AND p.deleted = 0
  );

-- ---------------------------------------------------------------------------
-- 标签（预置标签按名称匹配，避免写死自增 id）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO loan_product_tag (product_id, tag_id)
SELECT p.id, t.id
FROM loan_product p
JOIN loan_institution i ON i.id = p.institution_id AND i.deleted = 0
JOIN loan_tag t ON t.deleted = 0
WHERE p.deleted = 0 AND (
  (p.product_name = '薪金闪贷' AND i.institution_name = '星河银行' AND t.tag_name IN ('工薪族', '无抵押'))
  OR (p.product_name = '随心周转金' AND i.institution_name = '蓝海消费金融' AND t.tag_name IN ('工薪族', '随借随还', '无抵押'))
  OR (p.product_name = '公积金专享贷' AND i.institution_name = '星河银行' AND t.tag_name IN ('公积金', '无抵押'))
  OR (p.product_name = '公职优待贷' AND i.institution_name = '星河银行' AND t.tag_name IN ('社保', '无抵押'))
  OR (p.product_name = '经营周转贷' AND i.institution_name = '青梧小贷' AND t.tag_name IN ('小微企业', '企业主', '无抵押'))
  OR (p.product_name = '个体商户快贷' AND i.institution_name = '青梧小贷' AND t.tag_name IN ('个体工商户', '无抵押'))
  OR (p.product_name = '安居房抵贷' AND i.institution_name = '星河银行' AND t.tag_name = '房产抵押')
  OR (p.product_name = '途安车抵贷' AND i.institution_name = '途安汽车金融' AND t.tag_name = '车辆抵押')
  OR (p.product_name = '安心家装分期' AND i.institution_name = '蓝海消费金融' AND t.tag_name = '装修')
  OR (p.product_name = '学途教育分期' AND i.institution_name = '蓝海消费金融' AND t.tag_name = '教育')
  OR (p.product_name = '智选数码分期' AND i.institution_name = '蓝海消费金融' AND t.tag_name IN ('数码', '购物'))
  OR (p.product_name = '暑期消费专项' AND i.institution_name = '蓝海消费金融' AND t.tag_name = '购物')
);

-- ---------------------------------------------------------------------------
-- 图片登记（封面 + 机构 Logo）
-- ---------------------------------------------------------------------------
INSERT INTO loan_image (object_key, image_type, biz_id, sort, status, content_type, file_size)
SELECT 'loan/institution_logo/seed/logo-xinghe-bank.jpg', 'INSTITUTION_LOGO', CAST(i.id AS CHAR), 0, 'BOUND', 'image/jpeg', 130206
FROM loan_institution i
WHERE i.institution_name = '星河银行' AND i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM loan_image img WHERE img.object_key = 'loan/institution_logo/seed/logo-xinghe-bank.jpg');

INSERT INTO loan_image (object_key, image_type, biz_id, sort, status, content_type, file_size)
SELECT 'loan/institution_logo/seed/logo-lanhai-cf.jpg', 'INSTITUTION_LOGO', CAST(i.id AS CHAR), 0, 'BOUND', 'image/jpeg', 126776
FROM loan_institution i
WHERE i.institution_name = '蓝海消费金融' AND i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM loan_image img WHERE img.object_key = 'loan/institution_logo/seed/logo-lanhai-cf.jpg');

INSERT INTO loan_image (object_key, image_type, biz_id, sort, status, content_type, file_size)
SELECT 'loan/institution_logo/seed/logo-qingwu-micro.jpg', 'INSTITUTION_LOGO', CAST(i.id AS CHAR), 0, 'BOUND', 'image/jpeg', 133940
FROM loan_institution i
WHERE i.institution_name = '青梧小贷' AND i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM loan_image img WHERE img.object_key = 'loan/institution_logo/seed/logo-qingwu-micro.jpg');

INSERT INTO loan_image (object_key, image_type, biz_id, sort, status, content_type, file_size)
SELECT 'loan/institution_logo/seed/logo-tuan-auto.jpg', 'INSTITUTION_LOGO', CAST(i.id AS CHAR), 0, 'BOUND', 'image/jpeg', 121695
FROM loan_institution i
WHERE i.institution_name = '途安汽车金融' AND i.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM loan_image img WHERE img.object_key = 'loan/institution_logo/seed/logo-tuan-auto.jpg');

INSERT INTO loan_image (object_key, image_type, biz_id, sort, status, content_type, file_size)
SELECT v.object_key, 'COVER', CAST(p.id AS CHAR), 0, 'BOUND', 'image/jpeg', v.file_size
FROM (
  SELECT '薪金闪贷' AS product_name, '星河银行' AS institution_name, 'loan/cover/seed/cover-salary-flash.jpg' AS object_key, 214133 AS file_size
  UNION ALL SELECT '随心周转金', '蓝海消费金融', 'loan/cover/seed/cover-revolving.jpg', 224696
  UNION ALL SELECT '公积金专享贷', '星河银行', 'loan/cover/seed/cover-housing-fund.jpg', 204061
  UNION ALL SELECT '公职优待贷', '星河银行', 'loan/cover/seed/cover-civil-servant.jpg', 246982
  UNION ALL SELECT '经营周转贷', '青梧小贷', 'loan/cover/seed/cover-sme-working.jpg', 327622
  UNION ALL SELECT '个体商户快贷', '青梧小贷', 'loan/cover/seed/cover-self-employed.jpg', 280365
  UNION ALL SELECT '安居房抵贷', '星河银行', 'loan/cover/seed/cover-home-mortgage.jpg', 250242
  UNION ALL SELECT '途安车抵贷', '途安汽车金融', 'loan/cover/seed/cover-car-mortgage.jpg', 234345
  UNION ALL SELECT '安心家装分期', '蓝海消费金融', 'loan/cover/seed/cover-decoration.jpg', 296410
  UNION ALL SELECT '学途教育分期', '蓝海消费金融', 'loan/cover/seed/cover-education.jpg', 225175
  UNION ALL SELECT '智选数码分期', '蓝海消费金融', 'loan/cover/seed/cover-digital.jpg', 266568
  UNION ALL SELECT '暑期消费专项', '蓝海消费金融', 'loan/cover/seed/cover-summer-off.jpg', 190855
) v
JOIN loan_institution i ON i.institution_name = v.institution_name AND i.deleted = 0
JOIN loan_product p ON p.product_name = v.product_name AND p.institution_id = i.id AND p.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM loan_image img WHERE img.object_key = v.object_key);

UPDATE loan_institution i
JOIN loan_image img ON img.image_type = 'INSTITUTION_LOGO' AND img.biz_id = CAST(i.id AS CHAR) AND img.status = 'BOUND'
SET i.logo_key = img.object_key
WHERE i.institution_name IN ('星河银行', '蓝海消费金融', '青梧小贷', '途安汽车金融') AND i.deleted = 0;
