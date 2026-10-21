package org.example.risklendpro.risk.credit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.risk.credit.BehaviorScoringRules;

@Mapper
public interface BehaviorScoringRulesMapper {

    /**
     * 读取当前唯一激活的 B 卡规则。
     *
     * <p>版本选择顺序改为 {@code id DESC}（最新写入的规则行）：
     * 不再使用 {@code ORDER BY version DESC} 的字符串排序（v1.10 会排在 v1.9 之前，
     * 导致选中错误版本）。正常情况 where 条件已经唯一确定一行，
     * 排序只在唯一约束尚未生效、出现多 active 的异常场景下提供稳定、可解释的兜底选择。</p>
     *
     * <p>刻意不依赖 A1 迁移新增的 activated_at 列，使本类可以早于数据库迁移部署，
     * 避免因缺列导致评分链路整体不可用。</p>
     */
    @Select("SELECT * FROM behavior_scoring_rules WHERE is_active = 1 ORDER BY id DESC LIMIT 1")
    BehaviorScoringRules selectActiveRule();

    /**
     * 统计 active 版本数量：正常应为 0 或 1；大于 1 说明唯一约束未生效，需要人工介入。
     */
    @Select("SELECT COUNT(*) FROM behavior_scoring_rules WHERE is_active = 1")
    int countActiveRules();
}