package org.example.risklendpro.mapper.credit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.entity.credit.BehaviorScoringRules;

@Mapper
public interface BehaviorScoringRulesMapper {

    @Select("SELECT * FROM behavior_scoring_rules WHERE is_active = 1 ORDER BY version DESC LIMIT 1")
    BehaviorScoringRules selectActiveRule();
}
