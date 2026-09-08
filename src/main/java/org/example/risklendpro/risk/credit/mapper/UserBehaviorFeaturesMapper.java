package org.example.risklendpro.risk.credit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.risk.credit.UserBehaviorFeatures;

@Mapper
public interface UserBehaviorFeaturesMapper {

    @Select("SELECT * FROM user_behavior_features WHERE id_card = #{idCard} LIMIT 1")
    UserBehaviorFeatures selectByIdCard(@Param("idCard") String idCard);
}
