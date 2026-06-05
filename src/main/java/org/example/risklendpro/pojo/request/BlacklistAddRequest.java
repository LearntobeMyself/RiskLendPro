package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "黑名单写入请求")
public class BlacklistAddRequest {
    @Schema(description = "被执行人姓名/名称（支持*通配符）", example = "孙*", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "地区编码", example = "110112")
    private String areaCode;

    @Schema(description = "出生年份", example = "1986")
    private Integer birthYear;

    @Schema(description = "案号", example = "（2025）京0112执测L2号")
    private String caseNo;

    @Schema(description = "执行法院", example = "北京市通州区人民法院")
    private String courtName;

    @Schema(description = "被执行人履行情况", example = "全部未履行")
    private String dutyStatus;

    @Schema(description = "失信被执行人行为情况", example = "有履行能力而拒不履行生效法律文书确定义务")
    private String behaviorDetails;

    @Schema(description = "风险等级（HIGH/MEDIUM/LOW，未传则根据 dutyStatus 推导）", example = "HIGH")
    private String riskLevel;
}
