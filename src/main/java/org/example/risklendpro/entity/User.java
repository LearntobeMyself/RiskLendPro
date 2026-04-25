package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user")
public class User {
    private Long id;
    private String realName;
    private String phoneNumber;
    private String email;
    private String idCard;
    private String password;
    private String role;
    private String assessmentStatus;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}