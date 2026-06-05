package org.example.risklendpro.job;

import org.example.risklendpro.service.supplement.SupplementMaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupplementMaterialCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SupplementMaterialCleanupJob.class);

    @Autowired
    private SupplementMaterialService supplementMaterialService;

    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanupExpiredMaterials() {
        int removed = supplementMaterialService.cleanupExpiredMaterials();
        if (removed > 0) {
            log.info("Supplement material cleanup removed {} expired records", removed);
        }
    }
}
