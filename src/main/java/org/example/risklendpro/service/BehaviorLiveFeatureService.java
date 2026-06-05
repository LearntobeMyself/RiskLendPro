package org.example.risklendpro.service;

import lombok.Data;

public interface BehaviorLiveFeatureService {

    LiveFeatures aggregate(Long userId);

    @Data
    class LiveFeatures {
        private int maxOverdueDays;
        private int overduePeriodCount;
        private double onTimeRate;

        public double computeDelta() {
            return -maxOverdueDays * 3.0
                    - overduePeriodCount * 12.0
                    + onTimeRate * 40.0;
        }
    }
}
