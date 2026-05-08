package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "风控评估申请请求")
public class RiskAssessmentRequest {
    @Schema(description = "身份证号（唯一标识）", example = "110101198503151001")
    private String idCard;
    @Schema(description = "用户姓名", example = "陈优质")
    private String name;
    @Schema(description = "手机号码", example = "13800148001")
    private String phone;
    @Schema(description = "接收结果的邮箱", example = "chen_a@example.com")
    private String email;
    @Schema(description = "0-女, 1-男", example = "1")
    private Integer gender;
    @Schema(description = "出生日期 YYYY-MM-DD", example = "1985-03-15")
    private String birthday;
    @Schema(description = "学历", example = "本科")
    private String education;
    @Schema(description = "婚姻状况", example = "已婚")
    private String marriage;
    @Schema(description = "职业类型", example = "企事业单位")
    private String jobType;
    @Schema(description = "月收入", example = "15000以上")
    private String monthlyIncome;
    @Schema(description = "是否有房", example = "true")
    private Boolean hasHouse;
    @Schema(description = "是否有车", example = "true")
    private Boolean hasCar;
    @Schema(description = "紧急联系人电话", example = "13800148000")
    private String contactPhone;
}
