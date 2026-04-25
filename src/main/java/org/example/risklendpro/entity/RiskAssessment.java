package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("risk_assessment")
public class RiskAssessment {
    //TODO：风险评估有点问题，JWT身份认证，过期时间
    @TableId(type = IdType.INPUT)
    @TableField("apply_id")
    private String applyId;
    @TableField("user_id")
    private Long userId;
    private String idCard;
    private String name;
    private String phone;
    private String email;
    private Integer gender;
    private Date birthday;
    private String education;
    private String marriage;
    @TableField("job_type")
    private String jobType;
    @TableField("monthly_income")
    private String monthlyIncome;
    @TableField("has_house")
    private Boolean hasHouse;
    @TableField("has_car")
    private Boolean hasCar;
    @TableField("contact_phone")
    private String contactPhone;
    private String status;
    @TableField("sys_decision")
    private String sysDecision;
    @TableField("total_score")
    private Integer totalScore;
    @TableField("credit_limit")
    private BigDecimal creditLimit;
    @TableField("expire_date")
    private Date expireDate;
    @TableField("submit_time")
    private Date submitTime;
    @TableField("approval_time")
    private Date approvalTime;
    @TableField("is_final")
    private Boolean isFinal;
    @TableField("operator_id")
    private Long operatorId;
    @TableField("audit_remark")
    private String auditRemark;
}
