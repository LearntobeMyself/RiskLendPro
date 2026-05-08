package org.example.risklendpro.mapper.credit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.entity.credit.Blacklist;

import java.util.List;

/**
 * 黑名单Mapper - 操作credit_data_db数据库
 */
@Mapper
public interface BlacklistMapper {

    /**
     * 根据姓名模糊查询黑名单（支持黑名单中的通配符姓名如"李*梅"）
     */
    @Select("SELECT * FROM blacklist WHERE name LIKE #{pattern} AND (expire_at IS NULL OR expire_at > NOW())")
    List<Blacklist> selectByNamePattern(@Param("pattern") String pattern);

    /**
     * 根据姓名和地区编码查询黑名单
     */
    @Select("SELECT * FROM blacklist WHERE name LIKE #{pattern} AND area_code = #{areaCode} AND (expire_at IS NULL OR expire_at > NOW())")
    List<Blacklist> selectByNameAndArea(@Param("pattern") String pattern, @Param("areaCode") String areaCode);

    /**
     * 根据姓名、地区编码和出生年份查询黑名单
     */
    @Select("SELECT * FROM blacklist WHERE name LIKE #{pattern} AND area_code = #{areaCode} AND birth_year = #{birthYear} AND (expire_at IS NULL OR expire_at > NOW())")
    List<Blacklist> selectByNameAreaAndBirthYear(@Param("pattern") String pattern, @Param("areaCode") String areaCode, @Param("birthYear") Integer birthYear);

    /**
     * 查询所有未过期的黑名单记录
     */
    @Select("SELECT * FROM blacklist WHERE expire_at IS NULL OR expire_at > NOW()")
    List<Blacklist> selectAll();
}
