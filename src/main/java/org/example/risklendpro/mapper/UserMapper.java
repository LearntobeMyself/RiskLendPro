package org.example.risklendpro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.risklendpro.entity.User;

public interface UserMapper extends BaseMapper<User> {
    /**
     * 根据手机号查询用户
     * @param phoneNumber 手机号
     * @return 用户信息
     */
    User selectByPhoneNumber(String phoneNumber);
}