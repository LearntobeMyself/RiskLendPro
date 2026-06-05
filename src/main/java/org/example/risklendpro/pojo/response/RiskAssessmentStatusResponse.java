package org.example.risklendpro.pojo.response;

import org.example.risklendpro.pojo.dto.SupplementRequirement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Schema(description = "风控评估状态响应")
public class RiskAssessmentStatusResponse {
    @Schema(description = "申请ID")
    private String applyId;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "状态标题")
    private String statusTitle;
    @Schema(description = "是否终态")
    private boolean isFinal;
    @Schema(description = "补充材料状态: NONE/REQUIRED/SUBMITTED")
    private String supplementStatus;
    @Schema(description = "需上传材料清单")
    private List<SupplementRequirement> requiredMaterials;
    @Schema(description = "材料上传截止时间")
    private Date supplementDeadline;
    @Schema(description = "信用总分")
    private Integer totalScore;
    @Schema(description = "模型决策: APPROVE/MANUAL_REVIEW/REJECT")
    private String sysDecision;
    @Schema(description = "规则触发: BLACKLIST_L2/INCOME_OUTLIER/SCORE_ONLY，无则 null")
    private String ruleTrigger;
    @Schema(description = "已上传材料类型")
    private List<String> uploadedMaterialTypes;
}