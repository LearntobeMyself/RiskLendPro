package org.example.risklendpro.risk.bcard;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * B 卡成熟标签回填任务（阶段 A2）。
 *
 * <p>只处理表现期已经结束的样本，未成熟样本不会被提前打标签；
 * 任务幂等，重复执行不会重复覆盖已完成的标签。</p>
 */
@Component
public class BCardLabelTask {

    private static final Logger log = LoggerFactory.getLogger(BCardLabelTask.class);

    @Autowired
    private BCardLabelService bCardLabelService;

    @Value("${risk.b-card.label-batch-size:500}")
    private int batchSize = 500;

    @Scheduled(cron = "${risk.b-card.label-cron:0 30 3 * * ?}")
    @SchedulerLock(name = "bCardLabelBackfill", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void backfillMaturedLabels() {
        int count = bCardLabelService.backfillMaturedLabels(batchSize);
        log.info("B 卡成熟标签回填任务完成 labeled={}", count);
    }
}