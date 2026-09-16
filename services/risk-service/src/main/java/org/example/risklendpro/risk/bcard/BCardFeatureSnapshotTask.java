package org.example.risklendpro.risk.bcard;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BCardFeatureSnapshotTask {

    private static final Logger log = LoggerFactory.getLogger(BCardFeatureSnapshotTask.class);

    @Autowired
    private BCardFeatureService bCardFeatureService;

    @Scheduled(cron = "${risk.b-card.snapshot-cron:0 0 3 * * ?}")
    @SchedulerLock(name = "bCardFeatureSnapshot", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void captureDailySnapshots() {
        int count = bCardFeatureService.createSnapshotsForBCardUsers(LocalDate.now());
        log.info("B 卡特征快照生成完成 count={}", count);
    }
}
