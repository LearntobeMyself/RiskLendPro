package org.example.risklendpro.loan.borrow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量贷款审批请求")
public class BatchLoanApproveRequest {
    @Schema(description = "贷款ID列表")
    private List<Long> loanIds;
    @Schema(description = "APPROVE 或 REJECT")
    private String approveResult;
    @Schema(description = "审批备注")
    private String approveRemark;
}
