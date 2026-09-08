package org.example.risklendpro.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.user.entity.User;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE phone_number = #{phoneNumber}")
    User selectByPhoneNumber(String phoneNumber);
}
