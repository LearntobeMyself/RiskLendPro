package org.example.risklendpro.risk.score;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BehaviorLiveFeatureServiceTest {

    @Test
    void computeDelta_returnsZeroWhenNoHistory() {
        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(false);
        live.setOnTimeRate(1.0);

        assertEquals(0.0, live.computeDelta(), 0.001);
    }

    @Test
    void computeDelta_capsNegativeAdjustment() {
        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(true);
        live.setMaxOverdueDays(1000);
        live.setOverduePeriodCount(1000);

        assertEquals(-100.0, live.computeDelta(), 0.001);
    }

    @Test
    void computeDelta_capsPositiveAdjustment() {
        BehaviorLiveFeatureService.LiveFeatures live = new BehaviorLiveFeatureService.LiveFeatures();
        live.setHistoryAvailable(true);
        live.setOnTimeRate(1.0);

        assertEquals(20.0, live.computeDelta(), 0.001);
    }
}
