package org.example.risklendpro.loan.borrow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "管理员审批贷款申请请求")
public class LoanApproveRequest {
    @Schema(description = "贷款申请ID", example = "1")
    private Long loanId;
    @Schema(description = "APPROVE(通过) 或 REJECT(拒绝)", example = "APPROVE")
    private String approveResult;
    @Schema(description = "抵押物评估价（房抵/车抵审批通过时必填，管理员确认抵押物时录入）", example = "300000.00")
    private BigDecimal collateralValue;
    @Schema(description = "抵押物类型 HOUSE/VEHICLE，缺省时取借据上已记录的类型", example = "HOUSE")
    private String collateralType;
    @Schema(description = "审批评语", example = "借款金额合理，审批通过")
    private String approveRemark;
}