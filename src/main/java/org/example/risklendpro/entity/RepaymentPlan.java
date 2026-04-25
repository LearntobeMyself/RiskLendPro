package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("repayment_plan")
public class RepaymentPlan {
    @TableId(type = IdType.AUTO)
    @TableField("plan_id")
    private Long planId;
    @TableField("loan_id")
    private Long loanId;
    @TableField("user_id")
    private Long userId;
    @TableField("total_amount")
    private BigDecimal totalAmount;
    @TableField("paid_amount")
    private BigDecimal paidAmount;
    @TableField("remaining_amount")
    private BigDecimal remainingAmount;
    @TableField("total_periods")
    private Integer totalPeriods;
    @TableField("current_period")
    private Integer currentPeriod;
    private String status;
    @TableField("overdue_days")
    private Integer overdueDays;
    @TableField("overdue_level")
    private String overdueLevel;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
