package org.example.risklendpro.risk.bcard;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class JdbcBCardDecisionAuditRecorder implements BCardDecisionAuditRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO b_card_decision_audit
                (user_id, decision_type, model_version, model_hash, feature_version, policy_version,
                 rule_id, rule_hash, snapshot_ref, data_status, score, probability, multiplier, decision_allowed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcBCardDecisionAuditRecorder(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void record(AuditEntry entry) {
        jdbcTemplate.update(INSERT_SQL,
                entry.userId(), entry.decisionType(), entry.modelVersion(), entry.modelHash(),
                entry.featureVersion(), entry.policyVersion(), entry.ruleId(), entry.ruleHash(),
                entry.snapshotRef(), entry.dataStatus(), entry.score(), entry.probability(),
                entry.multiplier(), entry.decisionAllowed() == null ? null : (entry.decisionAllowed() ? 1 : 0));
    }
}