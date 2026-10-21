package org.example.risklendpro.risk.bcard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 模型注册、审批、激活与回滚（阶段 A5）。
 *
 * <p>硬性规则：</p>
 * <ul>
 *   <li>同一 model_version 不可重复注册（禁止覆盖历史）；</li>
 *   <li>只有 APPROVED 版本可以激活，激活前必须存在可回滚的上一稳定版本；</li>
 *   <li>回滚只能指向「曾经激活过」的版本（activatedAt 非空）；</li>
 *   <li>任何操作都不删除历史行，只改状态与时间戳。</li>
 * </ul>
 */
@Service
public class BCardModelRegistryService {

    private static final Logger log = LoggerFactory.getLogger(BCardModelRegistryService.class);

    public static final String STATUS_CANDIDATE = "CANDIDATE";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";

    @Autowired
    private BCardModelRegistryStore registryStore;

    /** 注册候选版本（不激活）。 */
    @Transactional
    public BCardModelRegistryStore.ModelRecord registerCandidate(String modelVersion,
                                                                 String featureVersion,
                                                                 String labelVersion,
                                                                 String policyVersion,
                                                                 String artifactJson,
                                                                 String metricsJson) {
        requireText(modelVersion, "modelVersion");
        requireText(featureVersion, "featureVersion");
        requireText(labelVersion, "labelVersion");
        requireText(policyVersion, "policyVersion");
        requireText(artifactJson, "artifactJson");
        if (registryStore.findByVersion(modelVersion).isPresent()) {
            throw new IllegalArgumentException("模型版本已存在，禁止覆盖历史版本: " + modelVersion);
        }
        registryStore.insert(new BCardModelRegistryStore.ModelRecord(
                modelVersion, sha256Hex(artifactJson), featureVersion, labelVersion, policyVersion,
                artifactJson, metricsJson, STATUS_CANDIDATE, null, null, null, null, null, null,
                LocalDateTime.now()));
        log.info("模型候选版本已注册 version={} hash={}", modelVersion, sha256Hex(artifactJson));
        return registryStore.findByVersion(modelVersion).orElseThrow();
    }

    /** 人工审批通过。 */
    @Transactional
    public BCardModelRegistryStore.ModelRecord approve(String modelVersion, String actor) {
        BCardModelRegistryStore.ModelRecord record = requireRecord(modelVersion);
        if (STATUS_ACTIVE.equals(record.status())) {
            return record;
        }
        if (!STATUS_CANDIDATE.equals(record.status()) && !STATUS_APPROVED.equals(record.status())) {
            throw new IllegalStateException("当前状态不允许审批: " + record.status());
        }
        registryStore.markApproved(modelVersion, actor == null ? "system" : actor);
        return requireRecord(modelVersion);
    }

    /**
     * 激活已审批版本。先下线旧 ACTIVE（保留为 RETIRED），再激活目标版本。
     */
    @Transactional
    public BCardModelRegistryStore.ModelRecord activate(String modelVersion, String actor) {
        BCardModelRegistryStore.ModelRecord record = requireRecord(modelVersion);
        if (STATUS_ACTIVE.equals(record.status())) {
            return record;
        }
        if (!STATUS_APPROVED.equals(record.status())) {
            throw new IllegalStateException("只有 APPROVED 版本可以激活，当前=" + record.status());
        }
        if (!BCardFeatureServiceImpl.FEATURE_VERSION.equals(record.featureVersion())) {
            throw new IllegalStateException("模型特征版本与当前在线口径不一致: model="
                    + record.featureVersion() + " online=" + BCardFeatureServiceImpl.FEATURE_VERSION);
        }
        registryStore.demoteActiveExcept(modelVersion);
        registryStore.markActive(modelVersion, "ACTIVATE");
        log.info("模型已激活 version={} by={}（旧版本保留为 RETIRED）", modelVersion, actor);
        return requireRecord(modelVersion);
    }

    /**
     * 回滚到曾激活过的稳定版本。只切换状态，不删除任何历史版本。
     */
    @Transactional
    public BCardModelRegistryStore.ModelRecord rollbackTo(String modelVersion, String actor) {
        BCardModelRegistryStore.ModelRecord record = requireRecord(modelVersion);
        if (STATUS_ACTIVE.equals(record.status())) {
            return record;
        }
        if (record.activatedAt() == null) {
            throw new IllegalStateException("只能回滚到曾激活过的版本: " + modelVersion);
        }
        if (STATUS_CANDIDATE.equals(record.status())) {
            throw new IllegalStateException("候选版本不能直接回滚激活");
        }
        registryStore.demoteActiveExcept(modelVersion);
        registryStore.markActive(modelVersion, "ROLLBACK");
        log.warn("模型已回滚 version={} by={}（历史版本保留）", modelVersion, actor);
        return requireRecord(modelVersion);
    }

    public Optional<BCardModelRegistryStore.ModelRecord> getActive() {
        return registryStore.findActive();
    }

    public List<BCardModelRegistryStore.ModelRecord> listVersions() {
        return registryStore.listAll();
    }

    private BCardModelRegistryStore.ModelRecord requireRecord(String modelVersion) {
        return registryStore.findByVersion(modelVersion)
                .orElseThrow(() -> new IllegalArgumentException("模型版本不存在: " + modelVersion));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}