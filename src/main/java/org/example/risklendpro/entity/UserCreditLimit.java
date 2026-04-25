package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_credit_limit")
public class UserCreditLimit {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("total_limit")
    private BigDecimal totalLimit;
    @TableField("used_limit")
    private BigDecimal usedLimit;
    @TableField("remaining_limit")
    private BigDecimal remainingLimit;
    @TableField("overdue_amount")
    private BigDecimal overdueAmount;
    @TableField("has_overdue")
    private Boolean hasOverdue;
    @TableField("last_update_time")
    private Date lastUpdateTime;
}