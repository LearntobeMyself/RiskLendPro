package org.example.risklendpro.pojo.request;

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
    @Schema(description = "额外批准的额度（通过时必填）", example = "10000.00")
    private BigDecimal additionalLimit;
    @Schema(description = "审批评语", example = "借款金额合理，审批通过")
    private String approveRemark;
}