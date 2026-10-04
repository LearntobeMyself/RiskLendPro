package org.example.risklendpro.loan.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@ConditionalOnProperty(prefix = "loan.schema", name = "auto-migrate-catalog", havingValue = "true", matchIfMissing = true)
public class LoanCatalogSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoanCatalogSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public LoanCatalogSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createTables();
            seedCategories();
            seedTags();
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Skip loan catalog migration: database unreachable ({})", e.getMessage());
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "借贷产品目录表自动迁移失败，请手工执行 sql/migration_loan_catalog.sql。原因: " + e.getMessage(), e);
        }
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_category (
                  code          VARCHAR(32)  NOT NULL PRIMARY KEY,
                  name          VARCHAR(32)  NOT NULL,
                  description   VARCHAR(255) NULL,
                  icon_key      VARCHAR(255) NULL,
                  sort          INT          NOT NULL DEFAULT 0,
                  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) COMMENT '借贷产品一级分类(固定4条)'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_tag (
                  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  tag_name      VARCHAR(20)  NOT NULL,
                  category_code VARCHAR(32)  NULL,
                  sort          INT          NOT NULL DEFAULT 0,
                  deleted       TINYINT      NOT NULL DEFAULT 0,
                  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY idx_category (category_code)
                ) COMMENT '产品标签'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_institution (
                  id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  institution_name VARCHAR(64)  NOT NULL,
                  institution_type VARCHAR(32)  NOT NULL,
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
                ) COMMENT '放贷机构'
                """);
        jdbcTemplate.execute("""
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
                  term_unit               VARCHAR(8)    NULL,
                  min_annual_rate         DECIMAL(5,2)  NULL,
                  max_annual_rate         DECIMAL(5,2)  NULL,
                  rate_calc_method        VARCHAR(8)    NULL,
                  rate_scope              VARCHAR(16)    NULL,
                  repayment_methods       JSON          NULL,
                  disbursement_time       VARCHAR(100)  NULL,
                  target_groups           JSON          NULL,
                  min_age                 INT           NULL,
                  max_age                 INT           NULL,
                  income_requirement      VARCHAR(255)  NULL,
                  credit_requirement      VARCHAR(255)  NULL,
                  occupation_requirement  VARCHAR(255)  NULL,
                  mortgage_required       VARCHAR(16)   NULL,
                  regions                 JSON          NULL,
                  extra_conditions        JSON          NULL,
                  materials               JSON          NULL,
                  fee_status              VARCHAR(8)    NULL,
                  fee_description         VARCHAR(1000) NULL,
                  prepayment_description  VARCHAR(500)  NULL,
                  remark                  VARCHAR(500)  NULL,
                  data_source             VARCHAR(255)  NULL,
                  verified_at             DATE          NULL,
                  last_off_shelf_time     DATETIME      NULL,
                  knowledge_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
                ) COMMENT '借贷产品'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_product_tag (
                  product_id BIGINT NOT NULL,
                  tag_id     BIGINT NOT NULL,
                  PRIMARY KEY (product_id, tag_id),
                  KEY idx_tag (tag_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_image (
                  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  object_key   VARCHAR(255) NOT NULL UNIQUE,
                  image_type   VARCHAR(32)  NOT NULL,
                  biz_id       VARCHAR(64)  NULL,
                  sort         INT          NOT NULL DEFAULT 0,
                  status       VARCHAR(16)  NOT NULL DEFAULT 'TEMP',
                  content_type VARCHAR(32)  NOT NULL,
                  file_size    BIGINT       NOT NULL,
                  create_by    BIGINT       NULL,
                  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_biz (image_type, biz_id),
                  KEY idx_status_time (status, create_time)
                ) COMMENT '对象存储图片登记'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS loan_operation_log (
                  id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  biz_type     VARCHAR(16) NOT NULL,
                  biz_id       BIGINT      NOT NULL,
                  action       VARCHAR(32) NOT NULL,
                  detail       JSON        NULL,
                  operator_id  BIGINT      NULL,
                  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_biz (biz_type, biz_id)
                )
                """);
    }

    private void seedCategories() {
        jdbcTemplate.update("""
                INSERT IGNORE INTO loan_category(code,name,description,sort) VALUES
                ('PERSONAL_CREDIT','个人信用贷','面向个人用户，无需抵押，主要依据个人信用、收入等进行授信',1),
                ('BUSINESS','企业经营贷','面向企业、个体工商户或企业主，用于经营周转',2),
                ('MORTGAGE','抵押贷款','用户需要提供房产、车辆等资产作为抵押',3),
                ('CONSUMER_INSTALLMENT','消费分期','针对特定消费场景提供的分期借款产品',4)
                """);
    }

    private void seedTags() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM loan_tag", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        Object[][] rows = {
                {"工薪族", "PERSONAL_CREDIT", 1}, {"公积金", "PERSONAL_CREDIT", 2}, {"社保", "PERSONAL_CREDIT", 3},
                {"小微企业", "BUSINESS", 1}, {"个体工商户", "BUSINESS", 2}, {"企业主", "BUSINESS", 3},
                {"房产抵押", "MORTGAGE", 1}, {"车辆抵押", "MORTGAGE", 2}, {"经营用途", "MORTGAGE", 3},
                {"装修", "CONSUMER_INSTALLMENT", 1}, {"教育", "CONSUMER_INSTALLMENT", 2},
                {"购物", "CONSUMER_INSTALLMENT", 3}, {"数码", "CONSUMER_INSTALLMENT", 4},
                {"无抵押", null, 1}, {"随借随还", null, 2}
        };
        for (Object[] row : rows) {
            jdbcTemplate.update(
                    "INSERT INTO loan_tag(tag_name, category_code, sort) VALUES (?,?,?)",
                    row[0], row[1], row[2]);
        }
        log.info("Seeded {} loan tags", rows.length);
    }
}
