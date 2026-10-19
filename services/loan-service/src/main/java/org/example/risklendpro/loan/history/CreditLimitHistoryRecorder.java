package org.example.risklendpro.loan.history;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;

/**
 * 额度历史快照写入（阶段 A2）。
 *
 * <p>{@code user_credit_limit} 只保存当前值，历史 asOfDate 无法还原当时的额度状态，
 * 会导致历史训练样本出现时间穿越。这里把额度状态按日切写入不可变的
 * {@code user_credit_limit_history}，供风控侧按 asOfDate 读取。</p>
 */
@Component
public class CreditLimitHistoryRecorder {

    private static final String RECORD_SQL = """
            INSERT INTO user_credit_limit_history
                (user_id, snapshot_date, total_limit, used_limit, remaining_limit,
                 overdue_amount, has_overdue, b_card_enabled, b_score, data_source)
            SELECT user_id, ?,
                   COALESCE(total_limit, 0), COALESCE(used_limit, 0), COALESCE(remaining_limit, 0),
                   COALESCE(overdue_amount, 0), COALESCE(has_overdue, 0), COALESCE(b_card_enabled, 0),
                   b_score, 'user_credit_limit_daily'
            FROM user_credit_limit
            ON DUPLICATE KEY UPDATE
                total_limit = VALUES(total_limit),
                used_limit = VALUES(used_limit),
                remaining_limit = VALUES(remaining_limit),
                overdue_amount = VALUES(overdue_amount),
                has_overdue = VALUES(has_overdue),
                b_card_enabled = VALUES(b_card_enabled),
                b_score = VALUES(b_score)
            """;

    private final JdbcTemplate jdbcTemplate;

    public CreditLimitHistoryRecorder(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    /**
     * 幂等写入指定日期的额度快照；同一天重复执行只刷新当天的行。
     *
     * @return 写入/更新的行数
     */
    public int recordDailySnapshot(LocalDate snapshotDate) {
        return jdbcTemplate.update(RECORD_SQL, Date.valueOf(snapshotDate));
    }
}