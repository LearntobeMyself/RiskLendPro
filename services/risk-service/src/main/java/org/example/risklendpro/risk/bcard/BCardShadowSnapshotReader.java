package org.example.risklendpro.risk.bcard;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** 读取用户最新 as-of 快照（只读）。 */
public interface BCardShadowSnapshotReader {

    Optional<ShadowFeature> findLatest(Long userId, String featureVersion);

    record ShadowFeature(LocalDate asOfDate, String featureVersion, Map<String, Double> features) {
    }
}