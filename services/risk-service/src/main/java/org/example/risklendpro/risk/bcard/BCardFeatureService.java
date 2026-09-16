package org.example.risklendpro.risk.bcard;

import org.example.risklendpro.risk.entity.BCardFeatureSnapshot;

import java.time.LocalDate;

public interface BCardFeatureService {

    BCardFeatureVector buildFeatureVector(Long userId, LocalDate asOfDate);

    BCardFeatureSnapshot createSnapshot(Long userId, LocalDate asOfDate);

    BCardFeatureSnapshot getLatestSnapshot(Long userId);

    int createSnapshotsForBCardUsers(LocalDate asOfDate);
}
