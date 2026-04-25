package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "额度调整响应")
public class LimitAdjustResponse {
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "原额度")
    private BigDecimal oldLimit;
    @Schema(description = "新额度")
    private BigDecimal newLimit;
    @Schema(description = "调整时间")
    private Date adjustTime;
}