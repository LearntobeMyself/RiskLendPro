package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("loan")
public class Loan {
    @TableId(type = IdType.AUTO)
    @TableField("loan_id")
    private Long loanId;
    @TableField("user_id")
    private Long userId;
    private BigDecimal amount;
    @TableField("term_months")
    private Integer termMonths;
    @TableField("interest_rate")
    private BigDecimal interestRate;
    @TableField("repayment_method")
    private String repaymentMethod;
    private String status;
    @TableField("apply_time")
    private Date applyTime;
    @TableField("approve_time")
    private Date approveTime;
    @TableField("disbursement_time")
    private Date disbursementTime;
    @TableField("auto_approved")
    private Boolean autoApproved;
    @TableField("reject_reason")
    private String rejectReason;
    @TableField("additional_limit")
    private BigDecimal additionalLimit;
    @TableField("operator_id")
    private Long operatorId;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
