package org.example.risklendpro.risk.bcard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JdbcBCardShadowSnapshotReader implements BCardShadowSnapshotReader {

    private static final String LATEST_SQL =
            "SELECT as_of_date, feature_version, feature_json FROM b_card_feature_snapshot "
                    + "WHERE user_id = ? AND feature_version = ? "
                    + "ORDER BY as_of_date DESC, id DESC LIMIT 1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcBCardShadowSnapshotReader(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public Optional<ShadowFeature> findLatest(Long userId, String featureVersion) {
        if (userId == null || featureVersion == null) {
            return Optional.empty();
        }
        List<ShadowFeature> rows = jdbcTemplate.query(LATEST_SQL, (rs, rowNum) -> {
            try {
                Map<String, Double> features = objectMapper.readValue(
                        rs.getString("feature_json"), new TypeReference<Map<String, Double>>() {});
                return new ShadowFeature(
                        rs.getDate("as_of_date").toLocalDate(),
                        rs.getString("feature_version"),
                        features);
            } catch (Exception e) {
                throw new IllegalStateException("解析影子特征失败", e);
            }
        }, userId, featureVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}