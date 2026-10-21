package org.example.risklendpro.loan.history;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 额度历史快照日切任务（阶段 A2）。
 *
 * <p>每天凌晨记录「前一天」的额度状态：这样风控侧对今天的 asOfDate 查询能命中最近一个
 * 已完成的日切快照，而不是回退到实时行；同时昨天的额度变化也不会被写进更早的历史快照。</p>
 */
@Component
public class CreditLimitHistoryTask {

    private static final Logger log = LoggerFactory.getLogger(CreditLimitHistoryTask.class);

    @Autowired
    private CreditLimitHistoryRecorder creditLimitHistoryRecorder;

    @Scheduled(cron = "${risk.b-card.limit-history-cron:0 45 2 * * ?}")
    @SchedulerLock(name = "creditLimitHistorySnapshot", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void recordYesterdaySnapshot() {
        LocalDate target = LocalDate.now().minusDays(1);
        int rows = creditLimitHistoryRecorder.recordDailySnapshot(target);
        log.info("额度历史快照写入完成 snapshotDate={} rows={}", target, rows);
    }
}