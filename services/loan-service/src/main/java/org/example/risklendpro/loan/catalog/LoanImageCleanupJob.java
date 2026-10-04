package org.example.risklendpro.loan.catalog;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanImageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LoanImageCleanupJob.class);

    private final LoanCatalogService loanCatalogService;

    public LoanImageCleanupJob(LoanCatalogService loanCatalogService) {
        this.loanCatalogService = loanCatalogService;
    }

    @Scheduled(cron = "0 15 3 * * ?")
    @SchedulerLock(name = "loanCatalogTempImageCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10S")
    public void cleanupTempImages() {
        int removed = loanCatalogService.cleanupTempImages();
        if (removed > 0) {
            log.info("Loan catalog temp image cleanup removed {} records", removed);
        }
    }
}
