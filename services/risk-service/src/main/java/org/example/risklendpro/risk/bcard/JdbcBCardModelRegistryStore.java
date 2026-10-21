package org.example.risklendpro.risk.bcard;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class JdbcBCardModelRegistryStore implements BCardModelRegistryStore {

    private static final String SELECT_COLUMNS =
            "model_version, model_hash, feature_version, label_version, policy_version, artifact_json, "
                    + "metrics_json, status, approved_by, approved_at, activated_at, retired_at, "
                    + "last_action, last_action_at, created_at";

    private static final RowMapper<ModelRecord> MAPPER = (rs, rowNum) -> new ModelRecord(
            rs.getString("model_version"),
            rs.getString("model_hash"),
            rs.getString("feature_version"),
            rs.getString("label_version"),
            rs.getString("policy_version"),
            rs.getString("artifact_json"),
            rs.getString("metrics_json"),
            rs.getString("status"),
            rs.getString("approved_by"),
            toLocal(rs.getTimestamp("approved_at")),
            toLocal(rs.getTimestamp("activated_at")),
            toLocal(rs.getTimestamp("retired_at")),
            rs.getString("last_action"),
            toLocal(rs.getTimestamp("last_action_at")),
            toLocal(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbcTemplate;

    public JdbcBCardModelRegistryStore(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.jdbcTemplate = new JdbcTemplate(primaryDataSource);
    }

    @Override
    public void insert(ModelRecord record) {
        jdbcTemplate.update("INSERT INTO b_card_model_registry "
                        + "(model_version, model_hash, feature_version, label_version, policy_version, "
                        + "artifact_json, metrics_json, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                record.modelVersion(), record.modelHash(), record.featureVersion(),
                record.labelVersion(), record.policyVersion(), record.artifactJson(),
                record.metricsJson(), record.status());
    }

    @Override
    public Optional<ModelRecord> findByVersion(String modelVersion) {
        List<ModelRecord> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM b_card_model_registry WHERE model_version = ?",
                MAPPER, modelVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<ModelRecord> findActive() {
        List<ModelRecord> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM b_card_model_registry WHERE status = 'ACTIVE' "
                        + "ORDER BY activated_at DESC, id DESC LIMIT 1", MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<ModelRecord> listAll() {
        return jdbcTemplate.query("SELECT " + SELECT_COLUMNS
                + " FROM b_card_model_registry ORDER BY id DESC", MAPPER);
    }

    @Override
    public void markApproved(String modelVersion, String actor) {
        jdbcTemplate.update("UPDATE b_card_model_registry SET "
                        + "status = 'APPROVED', approved_by = ?, approved_at = NOW(), "
                        + "last_action = 'APPROVE', last_action_at = NOW() "
                        + "WHERE model_version = ?",
                actor, modelVersion);
    }

    @Override
    public int demoteActiveExcept(String exceptVersion) {
        return jdbcTemplate.update("UPDATE b_card_model_registry SET "
                        + "status = 'RETIRED', retired_at = NOW(), last_action = 'RETIRE', last_action_at = NOW() "
                        + "WHERE status = 'ACTIVE' AND model_version <> ?",
                exceptVersion);
    }

    @Override
    public void markActive(String modelVersion, String action) {
        jdbcTemplate.update("UPDATE b_card_model_registry SET "
                        + "status = 'ACTIVE', activated_at = COALESCE(activated_at, NOW()), retired_at = NULL, "
                        + "last_action = ?, last_action_at = NOW() "
                        + "WHERE model_version = ?",
                action, modelVersion);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}