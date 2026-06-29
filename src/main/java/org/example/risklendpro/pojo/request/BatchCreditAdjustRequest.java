package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "批量额度调整请求")
public class BatchCreditAdjustRequest {
    @Schema(description = "用户ID列表")
    private List<Long> userIds;
    @Schema(description = "FIXED / INCREASE / DECREASE / PERCENT")
    private String mode;
    @Schema(description = "调整值（固定额度或增减金额或百分比）")
    private BigDecimal value;
    @Schema(description = "调整原因")
    private String reason;
}
