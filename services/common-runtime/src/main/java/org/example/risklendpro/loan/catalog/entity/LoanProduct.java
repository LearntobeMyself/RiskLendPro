package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("loan_product")
public class LoanProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productName;
    private Long institutionId;
    private String categoryCode;
    private String summary;
    private String status;
    private Integer everOnShelf;
    private Long minAmount;
    private Long maxAmount;
    private Integer minTerm;
    private Integer maxTerm;
    private String termUnit;
    private BigDecimal minAnnualRate;
    private BigDecimal maxAnnualRate;
    private String rateCalcMethod;
    private String rateScope;
    private String repaymentMethods;
    private String disbursementTime;
    private String targetGroups;
    private Integer minAge;
    private Integer maxAge;
    private String incomeRequirement;
    private String creditRequirement;
    private String occupationRequirement;
    private String mortgageRequired;
    private String regions;
    private String extraConditions;
    private String materials;
    private String feeStatus;
    private String feeDescription;
    private String prepaymentDescription;
    private String remark;
    private String dataSource;
    private LocalDate verifiedAt;
    private LocalDateTime lastOffShelfTime;
    private LocalDateTime knowledgeTime;
    private Integer sortWeight;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
