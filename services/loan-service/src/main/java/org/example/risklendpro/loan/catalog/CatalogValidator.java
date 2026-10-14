package org.example.risklendpro.loan.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.dto.ProductSaveRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CatalogValidator {

    private CatalogValidator() {
    }

    public static String requireName(String name, int max, String field) {
        if (name == null || name.isBlank()) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, field + "必填");
        }
        String trimmed = name.trim();
        if (trimmed.length() > max) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, field + "超长");
        }
        return trimmed;
    }

    public static void length(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, field + "超长");
        }
    }

    public static ProductSaveRequest.LoanInfo loanInfo(ProductSaveRequest.LoanInfo info) {
        return info == null ? new ProductSaveRequest.LoanInfo() : info;
    }

    public static ProductSaveRequest.Conditions conditions(ProductSaveRequest.Conditions conditions) {
        return conditions == null ? new ProductSaveRequest.Conditions() : conditions;
    }

    public static ProductSaveRequest.Others others(ProductSaveRequest.Others others) {
        return others == null ? new ProductSaveRequest.Others() : others;
    }

    public static String applyMortgageRule(String categoryCode, String mortgageRequired) {
        CatalogEnums.requireKnown(categoryCode, CatalogEnums.CATEGORY, "categoryCode");
        CatalogEnums.requireKnown(mortgageRequired, CatalogEnums.MORTGAGE, "mortgageRequired");
        String normalized = blankToNull(mortgageRequired);
        if ("MORTGAGE".equals(categoryCode)) {
            if (normalized != null && !"REQUIRED".equals(normalized)) {
                throw new CatalogBusinessException(CatalogErrorCodes.CATEGORY_RULE_CONFLICT, "抵押贷款必须需要抵押");
            }
            return "REQUIRED";
        }
        if ("REQUIRED".equals(normalized)) {
            throw new CatalogBusinessException(CatalogErrorCodes.CATEGORY_RULE_CONFLICT, "非抵押贷款不能为必须抵押");
        }
        if ("OPTIONAL".equals(normalized)) {
            if ("BUSINESS".equals(categoryCode)) {
                return "OPTIONAL";
            }
            throw new CatalogBusinessException(CatalogErrorCodes.CATEGORY_RULE_CONFLICT,
                    "个人信用贷和消费分期只能填无需抵押");
        }
        // 个人信用贷、消费分期、企业经营贷：未填一律视为无需抵押
        return "NONE";
    }

    public static void amountRange(Long min, Long max) {
        if (min != null && min <= 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "额度必须大于0");
        }
        if (max != null && max <= 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "额度必须大于0");
        }
        if (min != null && max != null && min > max) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "最小额度不能大于最大额度");
        }
    }

    public static String termUnit(Integer minTerm, Integer maxTerm, String termUnit) {
        if (minTerm != null && minTerm <= 0 || maxTerm != null && maxTerm <= 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "期限必须大于0");
        }
        if (minTerm != null && maxTerm != null && minTerm > maxTerm) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "最小期限不能大于最大期限");
        }
        if (minTerm != null || maxTerm != null) {
            if (termUnit == null || termUnit.isBlank()) {
                throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "有期限时必须填写期限单位");
            }
            CatalogEnums.requireKnown(termUnit, CatalogEnums.TERM_UNIT, "termUnit");
            if (!"MONTH".equals(termUnit)) {
                throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "期限单位只能按月");
            }
            return "MONTH";
        }
        return blankToNull(termUnit);
    }

    public static void rateRange(BigDecimal min, BigDecimal max) {
        checkRate(min);
        checkRate(max);
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "最低年化利率不能高于最高年化利率");
        }
    }

    private static void checkRate(BigDecimal rate) {
        if (rate == null) {
            return;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "年化利率不能为负");
        }
        if (rate.compareTo(new BigDecimal("24")) > 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "年化利率不能超过24%");
        }
    }

    public static void ageRange(Integer min, Integer max) {
        checkAge(min);
        checkAge(max);
        if (min != null && max != null && min > max) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "最小年龄不能大于最大年龄");
        }
    }

    private static void checkAge(Integer age) {
        if (age == null) {
            return;
        }
        if (age < 16 || age > 80) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "年龄须在16到80之间");
        }
    }

    public static void fee(String feeStatus, String feeDescription) {
        CatalogEnums.requireKnown(feeStatus, CatalogEnums.FEE_STATUS, "feeStatus");
        if ("HAS".equals(feeStatus) && (feeDescription == null || feeDescription.isBlank())) {
            throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID, "有其他费用时必须填写费用说明");
        }
        if ("NONE".equals(feeStatus) && feeDescription != null && !feeDescription.isBlank()) {
            throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID, "机构明示无其他费用时费用说明必须为空");
        }
    }

    public static List<String> targetGroups(List<String> groups) {
        List<String> unique = CatalogJson.unique(groups);
        for (String code : unique) {
            CatalogEnums.requireKnown(code, CatalogEnums.TARGET_GROUP, "targetGroups");
        }
        if (unique.contains("GENERAL") && unique.size() > 1) {
            throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID, "不限人群不能与其他人群同时出现");
        }
        return unique;
    }

    public static List<String> regions(List<String> regions) {
        List<String> unique = CatalogJson.unique(regions);
        if (unique.contains("全国") && unique.size() > 1) {
            throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID, "全国不能与省份同时出现");
        }
        return unique;
    }

    public static List<String> enumList(List<String> values, Map<String, String> dict, String field) {
        List<String> unique = CatalogJson.unique(values);
        for (String code : unique) {
            CatalogEnums.requireKnown(code, dict, field);
        }
        return unique;
    }

    public static Map<String, Object> extraConditions(String categoryCode, Map<String, Object> extra) {
        Map<String, Object> source = extra == null ? Map.of() : extra;
        List<String> allowed = CatalogEnums.extraFieldsOf(categoryCode);
        Set<String> allowedSet = new HashSet<>(allowed);
        for (String key : source.keySet()) {
            if (!allowedSet.contains(key)) {
                throw new CatalogBusinessException(CatalogErrorCodes.EXTRA_FIELD_INVALID, "补充条件包含非本分类字段: " + key);
            }
        }
        if ("PERSONAL_CREDIT".equals(categoryCode)) {
            return Map.of();
        }
        return source;
    }

    public static void validateExtraValues(String categoryCode, Map<String, Object> extra) {
        JsonNode tree = CatalogJson.readTree(CatalogJson.writeMap(extra));
        if ("BUSINESS".equals(categoryCode)) {
            positiveInt(tree.get("minEstablishYears"), "企业成立年限");
            positiveInt(tree.get("minOperatingMonths"), "经营时间");
            text(tree.get("revenueRequirement"), 255, "营业收入要求");
            text(tree.get("licenseRequirement"), 255, "营业执照要求");
        } else if ("MORTGAGE".equals(categoryCode)) {
            List<String> types = List.of();
            if (tree.has("collateralTypes") && !tree.get("collateralTypes").isNull()) {
                types = CatalogJson.readStringList(tree.get("collateralTypes").toString());
                enumList(types, CatalogEnums.COLLATERAL, "collateralTypes");
            }
            // 抵押贷款必须写明抵押物为房产或车辆，才能被识别为房抵 / 车抵
            if (types.isEmpty() || !(types.contains("HOUSE") || types.contains("VEHICLE"))) {
                throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID,
                        "抵押贷款必须写明抵押物为房产或车辆");
            }
            text(tree.get("collateralValueRequirement"), 255, "抵押物价值要求");
            if (tree.hasNonNull("maxLoanToValue")) {
                BigDecimal ltv = tree.get("maxLoanToValue").decimalValue();
                if (ltv.compareTo(BigDecimal.ZERO) < 0 || ltv.compareTo(new BigDecimal("100")) > 0) {
                    throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, "抵押率须在0到100之间");
                }
            }
        } else if ("CONSUMER_INSTALLMENT".equals(categoryCode)) {
            if (tree.has("scenes") && !tree.get("scenes").isNull()) {
                List<String> scenes = CatalogJson.readStringList(tree.get("scenes").toString());
                enumList(scenes, CatalogEnums.SCENE, "scenes");
            }
            text(tree.get("merchantScope"), 255, "合作商户范围");
        }
    }

    private static final List<String> IDENTITY_GROUPS =
            List.of("SALARIED", "PROVIDENT_FUND", "CIVIL_SERVANT", "SME_OWNER", "SELF_EMPLOYED");

    /**
     * 材料清单校验：房抵至少房产证、车抵至少行驶证和车辆登记证；
     * 材料清单里收集了身份证明时，必须覆盖所声明的特定身份（第 4 节对应证明）。
     */
    public static void validateMaterials(String categoryCode, Map<String, Object> extra,
                                         List<String> materials, List<String> targetGroups) {
        List<String> list = materials == null ? List.of() : materials;
        if ("MORTGAGE".equals(categoryCode)) {
            List<String> types = asStringList(extra.get("collateralTypes"));
            if (types.contains("HOUSE") && !containsAny(list, "房产证")) {
                throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID,
                        "房抵产品材料清单必须包含房产证");
            }
            if (types.contains("VEHICLE") && !(containsAny(list, "行驶证") && containsAny(list, "车辆登记证"))) {
                throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID,
                        "车抵产品材料清单必须包含行驶证和车辆登记证");
            }
        }
        List<String> constrained = (targetGroups == null ? List.<String>of() : targetGroups).stream()
                .filter(IDENTITY_GROUPS::contains)
                .toList();
        if (constrained.isEmpty() || !containsAny(list, "工资流水", "社保", "公积金", "工作证", "在职证明", "营业执照")) {
            return;
        }
        for (String group : constrained) {
            if (!containsAny(list, acceptableProofs(group))) {
                throw new CatalogBusinessException(CatalogErrorCodes.FIELD_DEPENDENCY_INVALID,
                        "适用人群含" + group + "时，材料清单必须包含对应身份证明");
            }
        }
    }

    private static String[] acceptableProofs(String group) {
        return switch (group) {
            case "SALARIED" -> new String[]{"工资流水", "社保"};
            case "PROVIDENT_FUND" -> new String[]{"公积金"};
            case "CIVIL_SERVANT" -> new String[]{"工作证", "在职证明"};
            case "SME_OWNER", "SELF_EMPLOYED" -> new String[]{"营业执照"};
            default -> new String[0];
        };
    }

    private static boolean containsAny(List<String> materials, String... keywords) {
        for (String material : materials) {
            if (material == null) {
                continue;
            }
            for (String keyword : keywords) {
                if (material.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        return List.of();
    }

    private static void positiveInt(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isInt() && !node.isLong()) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, field + "必须是整数");
        }
        if (node.intValue() <= 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.RANGE_INVALID, field + "必须大于0");
        }
    }

    private static void text(JsonNode node, int max, String field) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isTextual()) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, field + "必须是文本");
        }
        length(node.asText(), max, field);
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
