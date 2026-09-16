package org.example.risklendpro.risk.score;

import org.example.risklendpro.api.dto.LoanBehaviorSnapshot;
import org.example.risklendpro.risk.client.LoanServiceClient;
import org.example.risklendpro.risk.score.BehaviorLiveFeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BehaviorLiveFeatureServiceImpl implements BehaviorLiveFeatureService {

    @Autowired
    private LoanServiceClient loanServiceClient;

    @Override
    public LiveFeatures aggregate(Long userId) {
        LiveFeatures live = new LiveFeatures();
        if (userId == null) {
            live.setOnTimeRate(1.0);
            return live;
        }

        LoanBehaviorSnapshot snap = loanServiceClient.getLoanBehavior(userId);
        live.setMaxOverdueDays(snap.maxOverdueDays());
        live.setOverduePeriodCount(snap.overduePeriodCount());
        live.setOnTimeRate(snap.onTimeRate());
        return live;
    }
}
