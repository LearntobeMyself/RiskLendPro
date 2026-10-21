package org.example.risklendpro.risk.bcard;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 额度历史读取（阶段 A2）。
 *
 * <p>{@code user_credit_limit} 只有当前值、没有历史，直接读取会导致历史 asOfDate 快照
 * 使用未来数据（时间穿越）。因此历史快照的额度特征一律走 {@code user_credit_limit_history}
 * 不可变快照表。</p>
 */
public interface BCardCreditLimitHistoryReader {

    /** 取 snapshot_date &lt;= asOfDate 的最近一行；没有历史时返回空。 */
    Optional<CreditLimitAsOf> findAsOf(Long userId, LocalDate asOfDate);

    /**
     * as-of 额度快照。
     *
     * @param snapshotDate  该行反映的额度状态日期
     * @param totalLimit    总额度
     * @param usedLimit     已用额度
     * @param remainingLimit 剩余额度
     * @param overdueAmount 逾期金额
     * @param hasOverdue    是否逾期
     */
    record CreditLimitAsOf(LocalDate snapshotDate,
                           double totalLimit,
                           double usedLimit,
                           double remainingLimit,
                           double overdueAmount,
                           boolean hasOverdue) {
    }
}