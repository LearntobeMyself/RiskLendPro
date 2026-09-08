package org.example.risklendpro.risk.credit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.risk.credit.ScoringRules;

/**
 * 评分规则Mapper - 操作credit_data_db数据库（Python训练产出）
 */
@Mapper
public interface ScoringRulesMapper {

    /**
     * 查询当前激活的评分规则
     */
    @Select("SELECT * FROM scoring_rules WHERE is_active = 1 ORDER BY version DESC LIMIT 1")
    ScoringRules selectActiveRule();
}
