package org.example.risklendpro.risk.credit;

import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.risk.credit.mapper.BehaviorScoringRulesMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 A1 规则治理：版本选择顺序、单 active 约束与发布流程的回归护栏。
 */
class BehaviorScoringRulesGovernanceTest {

    @Test
    void activeRuleQueryMustNotOrderByVersionString() throws Exception {
        Select select = BehaviorScoringRulesMapper.class
                .getMethod("selectActiveRule")
                .getAnnotation(Select.class);

        assertNotNull(select, "selectActiveRule 必须声明 @Select");
        String sql = String.join(" ", select.value());

        assertFalse(sql.contains("version DESC"),
                "版本选择不能再依赖 version 字符串排序（v1.10 会排在 v1.9 之前）：" + sql);
        assertFalse(sql.contains("ORDER BY version"),
                "版本选择不能使用 ORDER BY version：" + sql);
        assertTrue(sql.contains("is_active = 1"), "必须筛选唯一 active 版本：" + sql);
        assertTrue(sql.contains("ORDER BY id DESC"),
                "必须使用稳定的 id 顺序兜底，且不得依赖 A1 迁移新增列：" + sql);
    }

    @Test
    void migrationEnforcesSingleActiveVersionAtDatabaseLevel() throws Exception {
        String sql = readRepoFile("sql/migration_b_card_rule_governance.sql");

        assertTrue(sql.contains("uk_bsr_single_active"), "缺少单 active 唯一索引");
        assertTrue(sql.contains("active_slot"), "缺少 active_slot 生成列");
        assertTrue(sql.contains("GENERATED ALWAYS AS"), "active_slot 必须是生成列");
        assertTrue(sql.contains("UPDATE behavior_scoring_rules"), "缺少多 active 收敛语句");
        assertTrue(sql.contains("rule_content LONGTEXT"),
                "rule_content 必须改为 LONGTEXT，否则两端内容哈希不一致");
    }

    @Test
    void activationScriptIsSingleTransactionAndKeepsHistory() throws Exception {
        String sql = readRepoFile("sql/activate_b_card_rule.sql");

        assertTrue(sql.contains("START TRANSACTION"), "激活必须显式开启事务");
        assertTrue(sql.contains("COMMIT"), "激活必须提交事务");
        assertTrue(sql.contains("SET is_active = 0 WHERE is_active = 1"),
                "激活必须先把旧 active 全部下线");
        assertTrue(sql.contains("SET is_active = 1"), "激活必须把目标版本上线");
        assertFalse(sql.contains("DELETE FROM behavior_scoring_rules"),
                "不得删除历史规则版本");
    }

    @Test
    void publisherValidatesReplaysAndActivatesInOneTransaction() throws Exception {
        String py = readRepoFile("risk-assessment/load_to_mysql.py");

        assertTrue(py.contains("def validate_b_card_rule"), "发布前必须做结构校验");
        assertTrue(py.contains("def replay_check"), "发布前必须做回放校验");
        assertTrue(py.contains("VALUES (%s, %s, %s, %s, %s, %s, %s, 0, NOW()"),
                "必须先以 inactive 形态写入");
        assertTrue(py.contains("SET is_active = 1, activated_at = NOW()"), "必须在同一事务内激活");
        assertTrue(py.contains("conn.rollback()"), "激活失败必须回滚");
        assertFalse(py.contains("DELETE FROM behavior_scoring_rules"),
                "发布流程不得删除历史规则版本");
    }

    private static String readRepoFile(String relative) throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            dir = dir.getParent();
        }
        throw new IOException("找不到仓库文件: " + relative + "，当前目录=" + Paths.get("").toAbsolutePath());
    }
}