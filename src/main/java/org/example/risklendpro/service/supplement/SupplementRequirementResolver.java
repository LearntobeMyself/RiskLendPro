package org.example.risklendpro.service.supplement;

import org.example.risklendpro.pojo.dto.SupplementRequirement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SupplementRequirementResolver {

    public List<SupplementRequirement> resolve(String auditRemark, List<String> riskTags) {
        Map<String, SupplementRequirement> merged = new LinkedHashMap<>();
        String remark = auditRemark != null ? auditRemark : "";

        if (remark.contains("BLACKLIST_MATCH_LEVEL_2")
                || containsTag(riskTags, "姓名+地域命中黑名单")) {
            put(merged, "ID_CARD_FRONT", "身份证正面", "请上传清晰、在有效期内的身份证人像面照片", true);
            put(merged, "ID_CARD_BACK", "身份证反面", "请上传清晰、在有效期内的身份证国徽面照片", true);
            put(merged, "RESIDENCE_PROOF", "户籍或居住证明", "户口本首页与本人页，或有效居住证/居住证明", true);
        }

        if (remark.contains("INCOME_OUTLIER")
                || containsTag(riskTags, "收入异常(需人工复核)")) {
            put(merged, "INCOME_PROOF", "收入证明", "近3个月工资流水、税单或单位盖章收入证明（任选其一）", true);
        }

        if (remark.contains("SCORE_MANUAL_REVIEW")
                || containsTag(riskTags, "信用分区间需人工审核")) {
            put(merged, "EMPLOYMENT_PROOF", "工作证明", "在职证明、劳动合同或工牌照片（可选）", false);
            put(merged, "CREDIT_EXPLANATION", "信用情况说明", "对征信或历史借贷情况的文字说明（可选）", false);
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * 主规则类型，供 status / 报告展示（材料清单仍按 remark+tags 合并解析）。
     */
    public String resolvePrimaryRuleCategory(String auditRemark, List<String> riskTags) {
        String remark = auditRemark != null ? auditRemark : "";
        boolean l2 = remark.contains("BLACKLIST_MATCH_LEVEL_2")
                || containsTag(riskTags, "姓名+地域命中黑名单");
        boolean income = remark.contains("INCOME_OUTLIER")
                || containsTag(riskTags, "收入异常(需人工复核)");
        if (l2 && income) {
            return "BLACKLIST_L2,INCOME_OUTLIER";
        }
        if (l2) {
            return "BLACKLIST_L2";
        }
        if (income) {
            return "INCOME_OUTLIER";
        }
        if (remark.contains("SCORE_MANUAL_REVIEW")
                || containsTag(riskTags, "信用分区间需人工审核")) {
            return "SCORE_ONLY";
        }
        return "NONE";
    }

    public String resolveRuleGate(List<String> riskTags) {
        return resolvePrimaryRuleCategory(null, riskTags);
    }

    public boolean hasRequiredMaterials(List<SupplementRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return false;
        }
        return requirements.stream().anyMatch(SupplementRequirement::isRequired);
    }

    private static void put(Map<String, SupplementRequirement> map,
                              String code, String label, String description, boolean required) {
        map.putIfAbsent(code, new SupplementRequirement(code, label, description, required));
    }

    private static boolean containsTag(List<String> tags, String keyword) {
        if (tags == null) {
            return false;
        }
        return tags.stream().anyMatch(t -> t != null && t.contains(keyword));
    }
}
