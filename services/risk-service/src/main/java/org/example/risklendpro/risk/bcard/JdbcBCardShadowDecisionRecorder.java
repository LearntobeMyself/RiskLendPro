package org.example.risklendpro.risk.bcard;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class JdbcBCardShadowDecisionRecorder implements BCardShadowDecisionRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO b_card_shadow_decision
                (user_id, as_of_date, model_version, feature_version, model_probability,
                 model_score, model_multiplier, current_multiplier, decision_differs, reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                feature_version = VALUES(feature_version),
                model_probability = VALUES(model_probability),
                model_score = VALUES(model_score),
                model_multiplier = VALUES(model_multiplier),
                current_multiplier = VALUES(current_multiplier),
                decision_differs = VALUES(decision_differs),
                reason = VALUES(reason)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcBCardShadowDecisionRecorder(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void record(ShadowDecision decision) {
        jdbcTemplate.update(INSERT_SQL,
                decision.userId(),
                java.sql.Date.valueOf(decision.asOfDate()),
                decision.modelVersion(),
                decision.featureVersion(),
                decision.modelProbability(),
                decision.modelScore(),
                decision.modelMultiplier(),
                decision.currentMultiplier(),
                decision.decisionDiffers() ? 1 : 0,
                decision.reason());
    }
}