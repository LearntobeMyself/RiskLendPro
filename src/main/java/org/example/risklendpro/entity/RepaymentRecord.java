package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("repayment_record")
public class RepaymentRecord {
    @TableField("record_id")
    private Long recordId;
    @TableField("plan_id")
    private Long planId;
    @TableField("loan_id")
    private Long loanId;
    private Integer period;
    private BigDecimal principal;
    private BigDecimal interest;
    private BigDecimal amount;
    @TableField("actual_amount")
    private BigDecimal actualAmount;
    @TableField("due_date")
    private Date dueDate;
    @TableField("repayment_date")
    private Date repaymentDate;
    private String status;
    @TableField("create_time")
    private Date createTime;
}