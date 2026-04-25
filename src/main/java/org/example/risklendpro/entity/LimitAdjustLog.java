package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("limit_adjust_log")
public class LimitAdjustLog {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("old_limit")
    private BigDecimal oldLimit;
    @TableField("new_limit")
    private BigDecimal newLimit;
    private String reason;
    @TableField("operator_id")
    private Long operatorId;
    @TableField("adjust_time")
    private Date adjustTime;
}