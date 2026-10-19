package org.example.risklendpro.risk.bcard;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 影子评分任务（阶段 A4）。默认关闭，只有显式配置
 * {@code risk.b-card.shadow.enabled=true} 才会注册与执行。
 *
 * <p>影子任务只写入 b_card_shadow_decision，不改变任何真实额度。</p>
 */
@Component
@ConditionalOnProperty(prefix = "risk.b-card.shadow", name = "enabled", havingValue = "true")
public class BCardShadowScoreTask {

    private static final Logger log = LoggerFactory.getLogger(BCardShadowScoreTask.class);

    @Autowired
    private BCardShadowScoreService bCardShadowScoreService;

    @Scheduled(cron = "${risk.b-card.shadow.cron:0 10 4 * * ?}")
    @SchedulerLock(name = "bCardShadowScore", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void runShadowScoring() {
        int count = bCardShadowScoreService.scoreShadowDecisions();
        log.info("B 卡影子评分任务完成 recorded={}", count);
    }
}