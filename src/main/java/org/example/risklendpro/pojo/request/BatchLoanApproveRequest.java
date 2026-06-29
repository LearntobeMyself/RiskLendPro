package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "批量贷款审批请求")
public class BatchLoanApproveRequest {
    @Schema(description = "贷款ID列表")
    private List<Long> loanIds;
    @Schema(description = "APPROVE 或 REJECT")
    private String approveResult;
    @Schema(description = "额外批准额度（批量通过时可选）")
    private BigDecimal additionalLimit;
    @Schema(description = "审批备注")
    private String approveRemark;
}
