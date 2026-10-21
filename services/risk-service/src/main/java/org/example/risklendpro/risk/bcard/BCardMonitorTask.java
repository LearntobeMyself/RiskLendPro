package org.example.risklendpro.risk.bcard;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 每日 B 卡监控（阶段 A5）。 */
@Component
public class BCardMonitorTask {

    private static final Logger log = LoggerFactory.getLogger(BCardMonitorTask.class);

    @Autowired
    private BCardMonitorService bCardMonitorService;

    @Scheduled(cron = "${risk.b-card.monitor-cron:0 30 4 * * ?}")
    @SchedulerLock(name = "bCardMonitor", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void runDailyMonitor() {
        int alerts = bCardMonitorService.runDaily(LocalDate.now()).size();
        log.info("B 卡监控任务完成 alerts={}", alerts);
    }
}