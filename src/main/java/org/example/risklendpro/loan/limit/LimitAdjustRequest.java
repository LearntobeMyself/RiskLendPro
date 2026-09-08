package org.example.risklendpro.loan.limit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "管理员调整额度请求")
public class LimitAdjustRequest {
    @Schema(description = "用户ID", example = "1")
    private Long userId;
    @Schema(description = "新额度", example = "15000.00")
    private BigDecimal newLimit;
    @Schema(description = "调整原因", example = "信用良好，提升额度")
    private String reason;
}