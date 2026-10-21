package org.example.risklendpro.risk.bcard;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@link BCardCreditLimitHistoryReader} 的 JDBC 实现，读取 {@code user_credit_limit_history}。
 */
@Component
public class JdbcBCardCreditLimitHistoryReader implements BCardCreditLimitHistoryReader {

    private static final String AS_OF_SQL =
            "SELECT snapshot_date, total_limit, used_limit, remaining_limit, overdue_amount, has_overdue "
                    + "FROM user_credit_limit_history "
                    + "WHERE user_id = ? AND snapshot_date <= ? "
                    + "ORDER BY snapshot_date DESC LIMIT 1";

    private final JdbcTemplate jdbcTemplate;

    public JdbcBCardCreditLimitHistoryReader(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public Optional<CreditLimitAsOf> findAsOf(Long userId, LocalDate asOfDate) {
        if (userId == null || asOfDate == null) {
            return Optional.empty();
        }
        List<CreditLimitAsOf> rows = jdbcTemplate.query(AS_OF_SQL, (rs, rowNum) -> new CreditLimitAsOf(
                rs.getDate("snapshot_date").toLocalDate(),
                toDouble(rs.getBigDecimal("total_limit")),
                toDouble(rs.getBigDecimal("used_limit")),
                toDouble(rs.getBigDecimal("remaining_limit")),
                toDouble(rs.getBigDecimal("overdue_amount")),
                rs.getBoolean("has_overdue")), userId, java.sql.Date.valueOf(asOfDate));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}