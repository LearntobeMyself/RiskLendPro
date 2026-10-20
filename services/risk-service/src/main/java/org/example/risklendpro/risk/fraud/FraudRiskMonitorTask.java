package org.example.risklendpro.risk.fraud;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 每日反欺诈/早期逾期预警扫描。 */
@Component
public class FraudRiskMonitorTask {

    private static final Logger log = LoggerFactory.getLogger(FraudRiskMonitorTask.class);

    @Autowired
    private FraudMonitorService fraudMonitorService;

    @Value("${risk.b-card.fraud-scan-limit:1000}")
    private int scanLimit = 1000;

    @Scheduled(cron = "${risk.b-card.fraud-scan-cron:0 0 5 * * ?}")
    @SchedulerLock(name = "fraudRiskMonitor", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void scan() {
        List<Map<String, Object>> warnings = fraudMonitorService.scanHighRiskUsers(scanLimit);
        log.info("反欺诈预警任务完成 highRisk={}", warnings.size());
    }
}