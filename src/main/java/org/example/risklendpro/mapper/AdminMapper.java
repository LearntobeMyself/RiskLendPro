package org.example.risklendpro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.risklendpro.entity.Admin;

public interface AdminMapper extends BaseMapper<Admin> {
    /**
     * 根据用户名查询管理员
     * @param username 用户名
     * @return 管理员信息
     */
    Admin selectByUsername(String username);
}