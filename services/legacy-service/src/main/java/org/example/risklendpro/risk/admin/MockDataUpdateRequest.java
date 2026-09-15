package org.example.risklendpro.risk.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新模拟数据请求")
public class MockDataUpdateRequest {
    @Schema(description = "身份证号", example = "110101199001011234")
    private String idCard;
    @Schema(description = "是否命中黑名单", example = "false")
    private Boolean isBlacklist;
    @Schema(description = "历史逾期次数", example = "0")
    private Integer overdueCount;
    @Schema(description = "多头借贷平台数", example = "2")
    private Integer loanCount;
    @Schema(description = "近期征信查询次数", example = "3")
    private Integer recentQueryCount;
}