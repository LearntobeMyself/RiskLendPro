-- 借贷产品目录（v3.1）：分类/标签/机构/产品/图片/操作日志
-- 只初始化分类和标签，不写入机构和产品。

CREATE TABLE IF NOT EXISTS loan_category (
  code          VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT 'PERSONAL_CREDIT/BUSINESS/MORTGAGE/CONSUMER_INSTALLMENT',
  name          VARCHAR(32)  NOT NULL,
  description   VARCHAR(255) NULL,
  icon_key      VARCHAR(255) NULL COMMENT '对象存储key',
  sort          INT          NOT NULL DEFAULT 0,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '借贷产品一级分类(固定4条)';

CREATE TABLE IF NOT EXISTS loan_tag (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tag_name      VARCHAR(20)  NOT NULL,
  category_code VARCHAR(32)  NULL COMMENT 'NULL=通用标签',
  sort          INT          NOT NULL DEFAULT 0,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category (category_code)
) COMMENT '产品标签';

CREATE TABLE IF NOT EXISTS loan_institution (
  id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  institution_name VARCHAR(64)  NOT NULL,
  institution_type VARCHAR(32)  NOT NULL COMMENT '见枚举InstitutionType',
  logo_key         VARCHAR(255) NULL,
  introduction     VARCHAR(1000) NULL,
  enabled          TINYINT      NOT NULL DEFAULT 1,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  version          INT          NOT NULL DEFAULT 0,
  create_by        BIGINT       NULL,
  update_by        BIGINT       NULL,
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_name (institution_name)
) COMMENT '放贷机构';

CREATE TABLE IF NOT EXISTS loan_product (
  id                      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  product_name            VARCHAR(64)   NOT NULL,
  institution_id          BIGINT        NOT NULL,
  category_code           VARCHAR(32)   NOT NULL,
  summary                 VARCHAR(500)  NULL,
  status                  VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
  ever_on_shelf           TINYINT       NOT NULL DEFAULT 0,
  min_amount              BIGINT        NULL,
  max_amount              BIGINT        NULL,
  min_term                INT           NULL,
  max_term                INT           NULL,
  term_unit               VARCHAR(8)    NULL COMMENT 'DAY/MONTH，有期限时必填',
  min_annual_rate         DECIMAL(5,2)  NULL,
  max_annual_rate         DECIMAL(5,2)  NULL,
  rate_calc_method        VARCHAR(8)    NULL COMMENT 'SIMPLE/IRR',
  rate_scope              VARCHAR(16)    NULL COMMENT 'ALL_IN/INTEREST_ONLY，NULL=未录入',
  repayment_methods       JSON          NULL COMMENT '["EQUAL_INSTALLMENT",...]',
  disbursement_time       VARCHAR(100)  NULL,
  target_groups           JSON          NULL COMMENT '字典target_group的code数组',
  min_age                 INT           NULL,
  max_age                 INT           NULL,
  income_requirement      VARCHAR(255)  NULL,
  credit_requirement      VARCHAR(255)  NULL,
  occupation_requirement  VARCHAR(255)  NULL,
  mortgage_required       VARCHAR(16)   NULL COMMENT 'NONE/REQUIRED/OPTIONAL',
  regions                 JSON          NULL COMMENT '["全国"] 或 省份名称数组',
  extra_conditions        JSON          NULL COMMENT '按分类的补充条件',
  materials               JSON          NULL COMMENT '材料字符串数组',
  fee_status              VARCHAR(8)    NULL COMMENT 'NONE/HAS，NULL=未录入',
  fee_description         VARCHAR(1000) NULL,
  prepayment_description  VARCHAR(500)  NULL,
  remark                  VARCHAR(500)  NULL,
  data_source             VARCHAR(255)  NULL COMMENT '信息来源，如机构官网/官方App/合作文件',
  verified_at             DATE          NULL COMMENT '信息核实日期',
  last_off_shelf_time     DATETIME      NULL COMMENT '最近一次下架时间',
  knowledge_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '知识块内容最近变化时间，A10增量同步用',
  sort_weight             INT           NOT NULL DEFAULT 0,
  deleted                 TINYINT       NOT NULL DEFAULT 0,
  version                 INT           NOT NULL DEFAULT 0,
  create_by               BIGINT        NULL,
  update_by               BIGINT        NULL,
  create_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status_category (status, category_code),
  KEY idx_institution (institution_id),
  KEY idx_name (product_name),
  KEY idx_knowledge_time (knowledge_time)
) COMMENT '借贷产品';

CREATE TABLE IF NOT EXISTS loan_product_tag (
  product_id BIGINT NOT NULL,
  tag_id     BIGINT NOT NULL,
  PRIMARY KEY (product_id, tag_id),
  KEY idx_tag (tag_id)
);

CREATE TABLE IF NOT EXISTS loan_image (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  object_key   VARCHAR(255) NOT NULL UNIQUE,
  image_type   VARCHAR(32)  NOT NULL COMMENT 'COVER/DETAIL/INSTITUTION_LOGO/CATEGORY_ICON',
  biz_id       VARCHAR(64)  NULL COMMENT '绑定对象ID：产品ID/机构ID/分类code，未绑定为NULL',
  sort         INT          NOT NULL DEFAULT 0,
  status       VARCHAR(16)  NOT NULL DEFAULT 'TEMP' COMMENT 'TEMP=已上传未绑定, BOUND=已绑定',
  content_type VARCHAR(32)  NOT NULL,
  file_size    BIGINT       NOT NULL,
  create_by    BIGINT       NULL,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_biz (image_type, biz_id),
  KEY idx_status_time (status, create_time)
) COMMENT '对象存储图片登记';

CREATE TABLE IF NOT EXISTS loan_operation_log (
  id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  biz_type     VARCHAR(16) NOT NULL COMMENT 'PRODUCT/INSTITUTION/TAG',
  biz_id       BIGINT      NOT NULL,
  action       VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/ON_SHELF/OFF_SHELF/DELETE/ENABLE/DISABLE/AUTO_OFF_SHELF',
  detail       JSON        NULL COMMENT '变更前后字段diff',
  operator_id  BIGINT      NULL,
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_biz (biz_type, biz_id)
);

INSERT IGNORE INTO loan_category(code,name,description,sort) VALUES
('PERSONAL_CREDIT','个人信用贷','面向个人用户，无需抵押，主要依据个人信用、收入等进行授信',1),
('BUSINESS','企业经营贷','面向企业、个体工商户或企业主，用于经营周转',2),
('MORTGAGE','抵押贷款','用户需要提供房产、车辆等资产作为抵押',3),
('CONSUMER_INSTALLMENT','消费分期','针对特定消费场景提供的分期借款产品',4);
