package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "用户额度响应")
public class UserCreditLimitResponse {
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "总额度")
    private BigDecimal totalLimit;
    @Schema(description = "已用额度")
    private BigDecimal usedLimit;
    @Schema(description = "剩余额度")
    private BigDecimal remainingLimit;
    @Schema(description = "逾期金额")
    private BigDecimal overdueAmount;
    @Schema(description = "是否有逾期")
    private Boolean hasOverdue;
    @Schema(description = "最后更新时间")
    private Date lastUpdateTime;
}
