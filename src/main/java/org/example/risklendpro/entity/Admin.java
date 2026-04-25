package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("admin")
public class Admin {
    private Long id;
    private String username;
    private String password;
    private String phoneNumber;
    private String email;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}