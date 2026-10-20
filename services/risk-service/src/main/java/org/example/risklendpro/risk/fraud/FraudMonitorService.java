package org.example.risklendpro.risk.fraud;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.bcard.BCardFeatureServiceImpl;
import org.example.risklendpro.risk.bcard.BCardShadowSnapshotReader;
import org.example.risklendpro.risk.credit.UserExternalFeatures;
import org.example.risklendpro.risk.credit.mapper.UserExternalFeaturesMapper;
import org.example.risklendpro.risk.score.CreditScoreEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反欺诈 / 早期逾期预警扫描（阶段 A5+）。
 *
 * <p>每天扫描启用 B 卡的用户，结合黑名单、外部征信特征、B 卡动态与申请频率，
 * 把 HIGH/CRITICAL 用户写入 b_card_fraud_warning，供人工复核。</p>
 */
@Service
public class FraudMonitorService {

    private static final Logger log = LoggerFactory.getLogger(FraudMonitorService.class);

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private UserExternalFeaturesMapper userExternalFeaturesMapper;

    @Autowired
    private CreditScoreEngine creditScoreEngine;

    @Autowired
    private BCardShadowSnapshotReader snapshotReader;

    @Autowired
    private FraudRiskService fraudRiskService;

    private final JdbcTemplate primary;

    public FraudMonitorService(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.primary = new JdbcTemplate(primaryDataSource);
    }

    /** 扫描高危用户并落预警；返回本次命中的高危用户。 */
    public List<Map<String, Object>> scanHighRiskUsers(int limit) {
        List<UserCreditLimit> users = userCreditLimitMapper.selectList(
                new QueryWrapper<UserCreditLimit>()
                        .eq("b_card_enabled", true)
                        .orderByAsc("user_id")
                        .last("LIMIT " + Math.max(1, limit)));
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (UserCreditLimit user : users) {
            try {
                Map<String, Object> info = loadUserInfo(user.getUserId());
                if (info == null) {
                    continue;
                }
                String name = (String) info.get("real_name");
                String idCard = (String) info.get("id_card");
                UserExternalFeatures external = idCard == null
                        ? null : userExternalFeaturesMapper.selectByIdCard(idCard);
                CreditScoreEngine.BlacklistMatchResult blacklist =
                        creditScoreEngine.checkBlacklist(name, idCard);
                Map<String, Double> bcard = snapshotReader
                        .findLatest(user.getUserId(), BCardFeatureServiceImpl.FEATURE_VERSION)
                        .map(BCardShadowSnapshotReader.ShadowFeature::features)
                        .orElse(Map.of());
                int applications30d = countApplications30d(user.getUserId());

                FraudRiskService.Result result = fraudRiskService.evaluateUser(
                        blacklist, external, bcard, applications30d);
                if (result.isHighRisk()) {
                    saveWarning(user.getUserId(), result);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userId", user.getUserId());
                    row.put("name", name);
                    row.put("score", result.score());
                    row.put("level", result.level().name());
                    row.put("summary", result.summary());
                    row.put("signalCodes", result.signals().stream()
                            .map(FraudRiskService.Signal::code).toList());
                    warnings.add(row);
                }
            } catch (Exception e) {
                log.warn("反欺诈扫描失败 userId={}", user.getUserId(), e);
            }
        }
        log.info("反欺诈扫描完成 scan={} highRisk={}", users.size(), warnings.size());
        return warnings;
    }

    public List<Map<String, Object>> listWarnings() {
        return primary.queryForList(
                "SELECT id, user_id, warn_date, rule_version, score, level, summary, status, created_at "
                        + "FROM b_card_fraud_warning "
                        + "WHERE warn_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) "
                        + "ORDER BY score DESC, id DESC LIMIT 200");
    }

    public int acknowledge(long warningId, String actor) {
        return primary.update("UPDATE b_card_fraud_warning SET status = 'ACKED', "
                        + "acked_by = ?, acked_at = NOW() WHERE id = ? AND status = 'OPEN'",
                actor == null ? "system" : actor, warningId);
    }

    private Map<String, Object> loadUserInfo(Long userId) {
        List<Map<String, Object>> rows = primary.queryForList(
                "SELECT real_name, id_card FROM `user` WHERE id = ? LIMIT 1", userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int countApplications30d(Long userId) {
        Long count = primary.queryForObject(
                "SELECT COUNT(*) FROM risk_assessment WHERE user_id = ? "
                        + "AND submit_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)", Long.class, userId);
        return count == null ? 0 : count.intValue();
    }

    private void saveWarning(Long userId, FraudRiskService.Result result) throws Exception {
        String signalsJson = new ObjectMapper().writeValueAsString(result.signals());
        primary.update("INSERT INTO b_card_fraud_warning "
                        + "(user_id, warn_date, rule_version, score, level, summary, signals_json, status) "
                        + "VALUES (?, CURDATE(), ?, ?, ?, ?, ?, 'OPEN') "
                        + "ON DUPLICATE KEY UPDATE score = VALUES(score), level = VALUES(level), "
                        + "summary = VALUES(summary), signals_json = VALUES(signals_json)",
                userId, FraudRiskService.RULE_VERSION, result.score(), result.level().name(),
                result.summary(), signalsJson);
    }
}