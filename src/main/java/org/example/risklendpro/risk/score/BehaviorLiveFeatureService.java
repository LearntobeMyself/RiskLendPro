package org.example.risklendpro.risk.score;

import lombok.Data;

public interface BehaviorLiveFeatureService {

    LiveFeatures aggregate(Long userId);

    @Data
    class LiveFeatures {
        private static final double MIN_DELTA = -100.0;
        private static final double MAX_DELTA = 20.0;

        private int maxOverdueDays;
        private int overduePeriodCount;
        private double onTimeRate;
        private boolean historyAvailable;

        public double computeDelta() {
            if (!historyAvailable) {
                return 0.0;
            }
            double rawDelta = -maxOverdueDays * 3.0
                    - overduePeriodCount * 12.0
                    + onTimeRate * 40.0;
            return Math.max(MIN_DELTA, Math.min(MAX_DELTA, rawDelta));
        }
    }
}
