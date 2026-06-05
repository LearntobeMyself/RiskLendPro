package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_credit_limit")
public class UserCreditLimit {
    @TableId(type = IdType.AUTO)
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
    @TableField("b_card_enabled")
    private Boolean bCardEnabled;
    @TableField("b_score")
    private BigDecimal bScore;
    @TableField("b_score_updated_at")
    private Date bScoreUpdatedAt;
    @TableField("last_update_time")
    private Date lastUpdateTime;
}
