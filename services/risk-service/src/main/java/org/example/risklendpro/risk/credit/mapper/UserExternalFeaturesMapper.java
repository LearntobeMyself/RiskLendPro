package org.example.risklendpro.risk.credit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.risk.credit.UserExternalFeatures;

import java.util.List;

/**
 * 用户外部特征Mapper - 操作credit_data_db数据库
 */
@Mapper
public interface UserExternalFeaturesMapper {

    /**
     * 根据身份证号查询用户外部特征
     */
    @Select("SELECT * FROM user_external_features WHERE id_card = #{idCard} LIMIT 1")
    UserExternalFeatures selectByIdCard(@Param("idCard") String idCard);

    /**
     * 根据身份证号批量查询用户外部特征（列表页用，避免逐人单查的 N+1）
     */
    @Select("<script>SELECT * FROM user_external_features WHERE id_card IN "
            + "<foreach collection='idCards' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + "</script>")
    java.util.List<UserExternalFeatures> selectByIdCards(@Param("idCards") List<String> idCards);

    /**
     * 根据用户ID(sk_id_curr)查询用户外部特征
     */
    @Select("SELECT * FROM user_external_features WHERE sk_id_curr = #{skIdCurr}")
    UserExternalFeatures selectBySkIdCurr(@Param("skIdCurr") Long skIdCurr);
}
