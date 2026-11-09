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

/**
 * 获客推荐相关表的幂等初始化。生产环境可设置 loan.schema.auto-migrate-recommendation=false，
 * 改为在发布窗口执行 sql/migration_recommendation.sql。
 */
@Component
@ConditionalOnProperty(prefix = "loan.schema", name = "auto-migrate-recommendation", havingValue = "true", matchIfMissing = true)
public class RecommendationSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendationSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public RecommendationSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createTables();
            seedDefaultConfigs();
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Skip recommendation migration: database unreachable ({})", e.getMessage());
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "获客推荐表自动迁移失败，请手工执行 sql/migration_recommendation.sql。原因: " + e.getMessage(), e);
        }
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_product_event (
                  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  user_id     BIGINT       NOT NULL,
                  product_id  BIGINT       NULL,
                  event_type  VARCHAR(32)  NOT NULL,
                  scene       VARCHAR(32)  NOT NULL,
                  session_id  VARCHAR(64)  NULL,
                  keyword     VARCHAR(100) NULL,
                  metadata    JSON         NULL,
                  request_id  VARCHAR(64)  NULL,
                  dedupe_key  VARCHAR(128) NULL,
                  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_event_user_time (user_id, created_at),
                  KEY idx_event_product_scene_time (product_id, scene, created_at),
                  KEY idx_event_type_scene_time (event_type, scene, created_at),
                  UNIQUE KEY uk_event_dedupe (dedupe_key)
                ) COMMENT '用户产品获客行为事件'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_product_preference (
                  id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  user_id       BIGINT        NOT NULL,
                  category_code VARCHAR(32)   NOT NULL,
                  target_group  VARCHAR(32)   NOT NULL DEFAULT '',
                  tag_id        BIGINT        NOT NULL DEFAULT 0,
                  score         DECIMAL(12,4) NOT NULL DEFAULT 0,
                  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_preference_dimension (user_id, category_code, target_group, tag_id),
                  KEY idx_preference_user_score (user_id, score)
                ) COMMENT '用户产品偏好画像'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_popularity (
                  id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  product_id  BIGINT        NOT NULL,
                  scene       VARCHAR(32)   NOT NULL,
                  impressions BIGINT        NOT NULL DEFAULT 0,
                  clicks      BIGINT        NOT NULL DEFAULT 0,
                  applies     BIGINT        NOT NULL DEFAULT 0,
                  ctr         DECIMAL(10,6) NOT NULL DEFAULT 0,
                  cvr         DECIMAL(10,6) NOT NULL DEFAULT 0,
                  score       DECIMAL(12,6) NOT NULL DEFAULT 0,
                  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_popularity_product_scene (product_id, scene),
                  KEY idx_popularity_scene_score (scene, score)
                ) COMMENT '产品曝光点击转化热度'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_similarity (
                  id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  product_id         BIGINT        NOT NULL,
                  similar_product_id BIGINT        NOT NULL,
                  score              DECIMAL(10,6) NOT NULL DEFAULT 0,
                  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_similarity_pair (product_id, similar_product_id),
                  KEY idx_similarity_product_score (product_id, score)
                ) COMMENT '产品内容相似度'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recommendation_impression (
                  request_id    VARCHAR(64)  NOT NULL PRIMARY KEY,
                  user_id       BIGINT       NOT NULL,
                  scene         VARCHAR(32)  NOT NULL,
                  product_ids   TEXT         NOT NULL,
                  model_version VARCHAR(32)  NOT NULL,
                  ab_group      VARCHAR(16)  NULL,
                  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_impression_user_time (user_id, created_at),
                  KEY idx_impression_scene_time (scene, created_at)
                ) COMMENT '推荐请求级曝光归因'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recommendation_config (
                  scene       VARCHAR(32) NOT NULL PRIMARY KEY,
                  config_json JSON        NOT NULL,
                  enabled     TINYINT     NOT NULL DEFAULT 1,
                  version     INT         NOT NULL DEFAULT 1,
                  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) COMMENT '推荐场景配置'
                """);
    }

    private void seedDefaultConfigs() {
        String defaultConfig = """
                {"weights":{"similarity":0.45,"popularity":0.25,"preference":0.20,"business":0.10},\
"candidateLimit":30,"explorationRate":0.10,"maxPerInstitution":1,"maxPerCategory":2,\
"fatigueDays":7,"fatiguePenalty":0.10}
                """.replaceAll("\\s+", "");
        for (String scene : new String[]{"HOME", "DETAIL", "CREDIT_PASSED", "POST_LOAN"}) {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO recommendation_config(scene, config_json, enabled, version)
                    VALUES (?, CAST(? AS JSON), 1, 1)
                    """, scene, defaultConfig);
        }
    }
}
