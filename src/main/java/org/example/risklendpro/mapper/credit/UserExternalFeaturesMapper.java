package org.example.risklendpro.mapper.credit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.entity.credit.UserExternalFeatures;

/**
 * 用户外部特征Mapper - 操作credit_data_db数据库
 */
@Mapper
public interface UserExternalFeaturesMapper {

    /**
     * 根据身份证号查询用户外部特征
     */
    @Select("SELECT * FROM user_external_features WHERE id_card = #{idCard}")
    UserExternalFeatures selectByIdCard(@Param("idCard") String idCard);
}
