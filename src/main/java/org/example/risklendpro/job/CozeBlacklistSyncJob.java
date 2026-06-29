package org.example.risklendpro.job;

import org.example.risklendpro.service.coze.CozeWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "risk.coze", name = "enabled", havingValue = "true")
public class CozeBlacklistSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CozeBlacklistSyncJob.class);

    @Autowired
    private CozeWorkflowService cozeWorkflowService;

    @Scheduled(cron = "${risk.coze.cron}")
    public void triggerBlacklistSync() {
        log.info("Coze blacklist sync job started");
        try {
            CozeWorkflowService.CozeWorkflowResult result = cozeWorkflowService.run();
            if (result.success()) {
                log.info("Coze blacklist sync job finished: {}", result.message());
            } else {
                log.error("Coze blacklist sync job failed: {}", result.message());
            }
        } catch (Exception e) {
            log.error("Coze blacklist sync job error: {}", e.getMessage(), e);
        }
    }
}
