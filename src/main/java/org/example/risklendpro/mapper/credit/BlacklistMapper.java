package org.example.risklendpro.mapper.credit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.entity.credit.Blacklist;

/**
 * 黑名单Mapper - 操作credit_data_db数据库
 */
@Mapper
public interface BlacklistMapper {

    /**
     * 根据身份证号查询黑名单
     */
    @Select("SELECT * FROM blacklist WHERE id_card = #{idCard} AND (expire_at IS NULL OR expire_at > NOW())")
    Blacklist selectByIdCard(@Param("idCard") String idCard);
}
