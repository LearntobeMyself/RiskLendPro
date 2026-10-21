package org.example.risklendpro.cs;

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
 * AI 客服表自动建表（幂等）。与 sql/migration_cs_service.sql 保持一致，
 * 生产或不便自动迁移时可关闭 cs.schema.auto-migrate 后手工执行该 SQL。
 */
@Component
@ConditionalOnProperty(prefix = "cs.schema", name = "auto-migrate", havingValue = "true", matchIfMissing = true)
public class CsSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CsSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public CsSchemaInitializer(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createTables();
        } catch (CannotGetJdbcConnectionException e) {
            log.warn("Skip cs schema migration: database unreachable ({})", e.getMessage());
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "AI 客服表自动迁移失败，请手工执行 sql/migration_cs_service.sql。原因: " + e.getMessage(), e);
        }
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cs_conversation (
                  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  user_id       BIGINT       NOT NULL COMMENT '发起会话的用户ID',
                  status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN=进行中, CLOSED=已结束, TRANSFERRED=已转人工',
                  last_message  VARCHAR(500) NULL COMMENT '最近一条消息摘要（列表展示用）',
                  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY idx_user (user_id),
                  KEY idx_user_status (user_id, status)
                ) COMMENT '客服会话'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cs_message (
                  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  conversation_id   BIGINT       NOT NULL COMMENT '所属会话ID',
                  user_id           BIGINT       NULL COMMENT '冗余：发送方用户ID（管理员回复时为NULL）',
                  role              VARCHAR(16)  NOT NULL COMMENT 'USER=用户, ASSISTANT=客服/大模型',
                  content           TEXT         NOT NULL COMMENT '消息内容',
                  intent            VARCHAR(32)  NULL COMMENT '意图路由：PRODUCT/REALTIME/SCRIPT/TRANSFER',
                  model             VARCHAR(64)  NULL COMMENT '生成该消息的大模型名称；规则话术为 fixed-script',
                  request_id        VARCHAR(64)  NULL COMMENT '大模型请求ID（证明非 if-else 的证据）',
                  prompt_tokens     INT          NULL COMMENT '提示词 token 数',
                  completion_tokens INT          NULL COMMENT '生成 token 数',
                  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_conversation (conversation_id),
                  KEY idx_user (user_id)
                ) COMMENT '客服消息'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cs_ticket (
                  id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  user_id         BIGINT        NOT NULL COMMENT '发起工单的用户ID',
                  conversation_id BIGINT        NULL COMMENT '来源会话ID（可为空）',
                  category        VARCHAR(32)   NOT NULL COMMENT '问题分类：ACCOUNT/RISK/LOAN/REPAYMENT/OTHER',
                  description     VARCHAR(1000) NOT NULL COMMENT '用户描述',
                  involves_money  TINYINT       NOT NULL DEFAULT 0 COMMENT '是否涉及资金 0/1',
                  biz_id          VARCHAR(64)   NULL COMMENT '关联业务ID（申请编号/借款编号等）',
                  status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=待处理, PROCESSING=处理中, CLOSED=已关闭',
                  handler_id      BIGINT        NULL COMMENT '处理人（管理员ID）',
                  result          VARCHAR(1000) NULL COMMENT '处理结果',
                  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  close_time      DATETIME      NULL COMMENT '关闭时间',
                  KEY idx_user (user_id),
                  KEY idx_status (status)
                ) COMMENT '转人工工单'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cs_audit_log (
                  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  user_id     BIGINT       NULL COMMENT '操作人（用户ID，客服操作为管理员ID）',
                  action      VARCHAR(32)  NOT NULL COMMENT '动作：SENSITIVE_BLOCK/REALTIME_QUERY/TICKET_CREATE/TICKET_PROCESS/TICKET_CLOSE',
                  target_type VARCHAR(32)  NULL COMMENT '目标类型：message/ticket/conversation',
                  target_id   VARCHAR(64)  NULL COMMENT '目标ID',
                  detail      VARCHAR(500) NULL COMMENT '详情摘要（已脱敏）',
                  client_ip   VARCHAR(64)  NULL COMMENT '客户端IP',
                  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_user (user_id),
                  KEY idx_action (action),
                  KEY idx_create_time (create_time)
                ) COMMENT '客服审计日志'
                """);
    }
}
