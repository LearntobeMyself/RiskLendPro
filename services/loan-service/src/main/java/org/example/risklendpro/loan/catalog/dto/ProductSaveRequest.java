package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class ProductSaveRequest {
    private String productName;
    private Long institutionId;
    private String categoryCode;
    private List<Long> tagIds;
    private String summary;
    private Long coverImageId;
    private List<Long> detailImageIds;
    private LoanInfo loanInfo;
    private Conditions conditions;
    private List<String> materials;
    private Others others;
    private String dataSource;
    private LocalDate verifiedAt;
    private Integer sortWeight;
    private Integer version;

    @Data
    public static class LoanInfo {
        private Long minAmount;
        private Long maxAmount;
        private Integer minTerm;
        private Integer maxTerm;
        private String termUnit;
        private BigDecimal minAnnualRate;
        private BigDecimal maxAnnualRate;
        private String rateCalcMethod;
        private String rateScope;
        private List<String> repaymentMethods;
        private String disbursementTime;
    }

    @Data
    public static class Conditions {
        private List<String> targetGroups;
        private Integer minAge;
        private Integer maxAge;
        private String incomeRequirement;
        private String creditRequirement;
        private String occupationRequirement;
        private String mortgageRequired;
        private List<String> regions;
        private Map<String, Object> extraConditions;
    }

    @Data
    public static class Others {
        private String feeStatus;
        private String feeDescription;
        private String prepaymentDescription;
        private String remark;
    }
}
