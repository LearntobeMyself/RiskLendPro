package org.example.risklendpro.risk.bcard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 A5 必需测试：模型发布与回滚。
 *
 * <p>使用内存注册表验证：候选注册 → 审批 → 激活 → 新版本激活（旧版本 RETIRED）
 * → 回滚（历史版本完整保留，不删除任何行）。</p>
 */
class BCardModelRegistryServiceTest {

    private BCardModelRegistryService service;
    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        service = new BCardModelRegistryService();
        store = new InMemoryStore();
        ReflectionTestUtils.setField(service, "registryStore", store);
    }

    @Test
    void publishFlowActivatesApprovedVersionAndKeepsHistory() {
        service.registerCandidate("A4-v1", BCardFeatureServiceImpl.FEATURE_VERSION,
                "LBL_V1", "POLICY_V1", "{\"model\":1}", "{\"auc\":0.8}");
        assertThrows(IllegalStateException.class, () -> service.activate("A4-v1", "ops"),
                "未审批版本不得激活");
        service.approve("A4-v1", "risk-lead");
        BCardModelRegistryStore.ModelRecord active = service.activate("A4-v1", "ops");

        assertEquals("ACTIVE", active.status());
        assertNotNull(active.activatedAt());
        assertEquals(64, active.modelHash().length());

        service.registerCandidate("A4-v2", BCardFeatureServiceImpl.FEATURE_VERSION,
                "LBL_V1", "POLICY_V1", "{\"model\":2}", null);
        service.approve("A4-v2", "risk-lead");
        service.activate("A4-v2", "ops");

        assertEquals("A4-v2", service.getActive().orElseThrow().modelVersion());
        assertEquals(2, service.listVersions().size(), "历史版本必须完整保留");
        Map<String, String> statuses = new LinkedHashMap<>();
        service.listVersions().forEach(r -> statuses.put(r.modelVersion(), r.status()));
        assertEquals("RETIRED", statuses.get("A4-v1"));
        assertEquals("ACTIVE", statuses.get("A4-v2"));
    }

    @Test
    void rollbackRestoresPreviousStableVersionWithoutDeletingHistory() {
        activate("A4-v1");
        activate("A4-v2");

        BCardModelRegistryStore.ModelRecord rolledBack = service.rollbackTo("A4-v1", "ops");

        assertEquals("A4-v1", rolledBack.modelVersion());
        assertEquals("ACTIVE", rolledBack.status());
        assertEquals("ROLLBACK", rolledBack.lastAction());
        assertEquals("RETIRED", store.findByVersion("A4-v2").orElseThrow().status());
        assertEquals(2, service.listVersions().size());
    }

    @Test
    void rollbackRejectsVersionThatWasNeverActive() {
        service.registerCandidate("A4-v3", BCardFeatureServiceImpl.FEATURE_VERSION,
                "LBL_V1", "POLICY_V1", "{\"model\":3}", null);
        service.approve("A4-v3", "risk-lead");

        assertThrows(IllegalStateException.class, () -> service.rollbackTo("A4-v3", "ops"));
    }

    @Test
    void rejectsDuplicateModelVersion() {
        service.registerCandidate("A4-dup", BCardFeatureServiceImpl.FEATURE_VERSION,
                "LBL_V1", "POLICY_V1", "{\"model\":1}", null);

        assertThrows(IllegalArgumentException.class, () -> service.registerCandidate(
                "A4-dup", BCardFeatureServiceImpl.FEATURE_VERSION, "LBL_V1", "POLICY_V1", "{}", null));
    }

    @Test
    void rejectsActivationWhenFeatureVersionDiffersFromOnline() {
        service.registerCandidate("A4-old", "BF_V2.1", "LBL_V1", "POLICY_V1", "{\"model\":1}", null);
        service.approve("A4-old", "risk-lead");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.activate("A4-old", "ops"));
        assertTrue(ex.getMessage().contains("特征版本"));
    }

    private void activate(String version) {
        service.registerCandidate(version, BCardFeatureServiceImpl.FEATURE_VERSION,
                "LBL_V1", "POLICY_V1", "{\"model\":\"" + version + "\"}", null);
        service.approve(version, "risk-lead");
        service.activate(version, "ops");
    }

    /** 内存注册表：仅用于测试发布/回滚状态机。 */
    static class InMemoryStore implements BCardModelRegistryStore {
        private final Map<String, ModelRecord> rows = new LinkedHashMap<>();

        @Override
        public void insert(ModelRecord record) {
            rows.put(record.modelVersion(), record);
        }

        @Override
        public Optional<ModelRecord> findByVersion(String modelVersion) {
            return Optional.ofNullable(rows.get(modelVersion));
        }

        @Override
        public Optional<ModelRecord> findActive() {
            return rows.values().stream()
                    .filter(r -> "ACTIVE".equals(r.status()))
                    .findFirst();
        }

        @Override
        public List<ModelRecord> listAll() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void markApproved(String modelVersion, String actor) {
            ModelRecord r = rows.get(modelVersion);
            rows.put(modelVersion, new ModelRecord(r.modelVersion(), r.modelHash(), r.featureVersion(),
                    r.labelVersion(), r.policyVersion(), r.artifactJson(), r.metricsJson(), "APPROVED",
                    actor, LocalDateTime.now(), r.activatedAt(), r.retiredAt(), "APPROVE",
                    LocalDateTime.now(), r.createdAt()));
        }

        @Override
        public int demoteActiveExcept(String exceptVersion) {
            int changed = 0;
            for (Map.Entry<String, ModelRecord> entry : new ArrayList<>(rows.entrySet())) {
                ModelRecord r = entry.getValue();
                if ("ACTIVE".equals(r.status()) && !r.modelVersion().equals(exceptVersion)) {
                    rows.put(entry.getKey(), new ModelRecord(r.modelVersion(), r.modelHash(), r.featureVersion(),
                            r.labelVersion(), r.policyVersion(), r.artifactJson(), r.metricsJson(), "RETIRED",
                            r.approvedBy(), r.approvedAt(), r.activatedAt(), LocalDateTime.now(),
                            "RETIRE", LocalDateTime.now(), r.createdAt()));
                    changed++;
                }
            }
            return changed;
        }

        @Override
        public void markActive(String modelVersion, String action) {
            ModelRecord r = rows.get(modelVersion);
            rows.put(modelVersion, new ModelRecord(r.modelVersion(), r.modelHash(), r.featureVersion(),
                    r.labelVersion(), r.policyVersion(), r.artifactJson(), r.metricsJson(), "ACTIVE",
                    r.approvedBy(), r.approvedAt(),
                    r.activatedAt() == null ? LocalDateTime.now() : r.activatedAt(),
                    null, action, LocalDateTime.now(), r.createdAt()));
        }
    }
}