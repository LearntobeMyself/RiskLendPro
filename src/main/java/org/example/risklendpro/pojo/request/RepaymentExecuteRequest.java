package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "执行还款请求")
public class RepaymentExecuteRequest {
    @Schema(description = "还款计划ID", example = "1")
    private Long planId;
    @Schema(description = "期数", example = "1")
    private Integer period;
    @Schema(description = "还款金额", example = "1833.33")
    private BigDecimal amount;
}