package org.example.risklendpro.risk.bcard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 模型注册表存储（便于用内存实现做发布/回滚测试）。 */
public interface BCardModelRegistryStore {

    void insert(ModelRecord record);

    Optional<ModelRecord> findByVersion(String modelVersion);

    Optional<ModelRecord> findActive();

    List<ModelRecord> listAll();

    void markApproved(String modelVersion, String actor);

    /** 把除 exceptVersion 外的 ACTIVE 版本下线为 RETIRED（不删除任何行）。 */
    int demoteActiveExcept(String exceptVersion);

    void markActive(String modelVersion, String action);

    record ModelRecord(
            String modelVersion,
            String modelHash,
            String featureVersion,
            String labelVersion,
            String policyVersion,
            String artifactJson,
            String metricsJson,
            String status,
            String approvedBy,
            LocalDateTime approvedAt,
            LocalDateTime activatedAt,
            LocalDateTime retiredAt,
            String lastAction,
            LocalDateTime lastActionAt,
            LocalDateTime createdAt
    ) {
    }
}