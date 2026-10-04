package org.example.risklendpro.loan.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class KnowledgeRenderer {

    public static final String RISK_TIP = "贷款有风险，借款需谨慎。以上信息仅供参考，以机构实际审批和合同为准。";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat AMOUNT = new DecimalFormat("#,###");

    private KnowledgeRenderer() {
    }

    public static String render(LoanProduct product, String institutionName, String institutionTypeName, List<String> tagNames) {
        if ("OFF_SHELF".equals(product.getStatus())) {
            return renderOffShelf(product.getProductName(), institutionName, product.getLastOffShelfTime());
        }
        return renderOnShelf(product, institutionName, institutionTypeName, tagNames);
    }

    public static String renderOffShelf(String productName, String institutionName, LocalDateTime offShelfTime) {
        return """
                【产品】%s（机构：%s）
                状态：已下架（下架时间：%s）。平台已不再展示该产品的额度、利率、费用和申请条件。
                说明：如需了解该产品现状，请咨询%s官方渠道。
                """.formatted(nz(productName), nz(institutionName), dateText(offShelfTime), nz(institutionName)).trim();
    }

    public static String renderOnShelf(LoanProduct product, String institutionName, String institutionTypeName, List<String> tagNames) {
        String feeDescription = "NONE".equals(product.getFeeStatus())
                ? "无"
                : text(product.getFeeDescription());
        return """
                【产品】%s（机构：%s，%s）
                状态：在架；信息来源：%s；核实日期：%s
                分类：%s；标签：%s
                简介：%s
                借款额度：%s
                借款期限：%s
                参考年化利率：%s；计算方式：%s；利率口径：%s
                除利息外的其他费用：%s；费用说明：%s
                还款方式：%s
                预计放款时间：%s
                适用人群：%s
                年龄要求：%s
                收入要求：%s
                征信要求：%s
                职业要求：%s
                是否需要抵押：%s
                可申请地区：%s
                补充条件：%s
                申请材料：%s
                提前还款：%s
                备注：%s
                申请方式：本平台不受理申请，请通过%s官方渠道申请。
                说明：本段中“暂无数据”表示平台未录入该项，不代表没有、不收费或不限制。以上数字截至核实日期 %s，最终额度、利率、费用和能否获批以机构审批和合同为准。
                风险提示：贷款有风险，借款需谨慎，请根据自身还款能力合理借款。
                """.formatted(
                nz(product.getProductName()),
                nz(institutionName),
                nz(institutionTypeName),
                text(product.getDataSource()),
                dateText(product.getVerifiedAt()),
                CatalogEnums.CATEGORY.getOrDefault(product.getCategoryCode() == null ? "" : product.getCategoryCode(), "暂无数据"),
                joinNames(tagNames),
                text(product.getSummary()),
                amountText(product.getMinAmount(), product.getMaxAmount()),
                termText(product.getMinTerm(), product.getMaxTerm(), product.getTermUnit()),
                rateText(product.getMinAnnualRate(), product.getMaxAnnualRate()),
                dictText(product.getRateCalcMethod(), CatalogEnums.RATE_CALC),
                dictText(product.getRateScope(), CatalogEnums.RATE_SCOPE),
                dictText(product.getFeeStatus(), CatalogEnums.FEE_STATUS),
                feeDescription,
                codesText(CatalogJson.readStringList(product.getRepaymentMethods()), CatalogEnums.REPAYMENT_METHOD),
                text(product.getDisbursementTime()),
                targetGroupsText(CatalogJson.readStringList(product.getTargetGroups())),
                ageText(product.getMinAge(), product.getMaxAge()),
                text(product.getIncomeRequirement()),
                text(product.getCreditRequirement()),
                text(product.getOccupationRequirement()),
                dictText(product.getMortgageRequired(), CatalogEnums.MORTGAGE),
                regionsText(CatalogJson.readStringList(product.getRegions())),
                extraConditionsText(product.getCategoryCode(), product.getExtraConditions()),
                joinNames(CatalogJson.readStringList(product.getMaterials())),
                text(product.getPrepaymentDescription()),
                text(product.getRemark()),
                nz(institutionName),
                dateText(product.getVerifiedAt())
        ).trim();
    }

    public static String amountText(Long min, Long max) {
        if (min != null && max != null) {
            return AMOUNT.format(min) + " 至 " + AMOUNT.format(max) + " 元";
        }
        if (min != null) {
            return AMOUNT.format(min) + " 元起";
        }
        if (max != null) {
            return "最高 " + AMOUNT.format(max) + " 元";
        }
        return "暂无数据";
    }

    public static String termText(Integer min, Integer max, String unit) {
        String unitName = unit == null ? "" : CatalogEnums.TERM_UNIT.getOrDefault(unit, "");
        if (min != null && max != null) {
            return min + " 至 " + max + " " + unitName;
        }
        if (min != null) {
            return min + " " + unitName + "起";
        }
        if (max != null) {
            return "最高 " + max + " " + unitName;
        }
        return "暂无数据";
    }

    public static String rateText(BigDecimal min, BigDecimal max) {
        if (min != null && max != null) {
            return strip(min) + "% 至 " + strip(max) + "%";
        }
        if (min != null) {
            return strip(min) + "% 起";
        }
        if (max != null) {
            return "最高 " + strip(max) + "%";
        }
        return "暂无数据";
    }

    public static String ageText(Integer min, Integer max) {
        if (min != null && max != null) {
            return min + " 至 " + max + " 岁";
        }
        if (min != null) {
            return min + " 岁及以上";
        }
        if (max != null) {
            return max + " 岁及以下";
        }
        return "暂无数据";
    }

    public static String targetGroupsText(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return "暂无数据";
        }
        if (groups.size() == 1 && "GENERAL".equals(groups.get(0))) {
            return "不限（机构明示）";
        }
        return groups.stream().map(c -> CatalogEnums.TARGET_GROUP.getOrDefault(c, c)).collect(Collectors.joining("、"));
    }

    public static String regionsText(List<String> regions) {
        if (regions == null || regions.isEmpty()) {
            return "暂无数据";
        }
        return String.join("、", regions);
    }

    public static String extraConditionsText(String categoryCode, String extraJson) {
        if ("PERSONAL_CREDIT".equals(categoryCode)) {
            return "本分类不设补充条件";
        }
        JsonNode extra = CatalogJson.readTree(extraJson);
        List<String> parts = new ArrayList<>();
        if ("BUSINESS".equals(categoryCode)) {
            parts.add("企业成立年限下限：" + years(extra.get("minEstablishYears")));
            parts.add("经营时间下限：" + months(extra.get("minOperatingMonths")));
            parts.add("营业收入要求：" + nodeText(extra.get("revenueRequirement")));
            parts.add("营业执照要求：" + nodeText(extra.get("licenseRequirement")));
        } else if ("MORTGAGE".equals(categoryCode)) {
            parts.add("可接受抵押物类型：" + enumArray(extra.get("collateralTypes"), CatalogEnums.COLLATERAL));
            parts.add("抵押物价值要求：" + nodeText(extra.get("collateralValueRequirement")));
            parts.add("抵押率上限：" + percent(extra.get("maxLoanToValue")));
        } else if ("CONSUMER_INSTALLMENT".equals(categoryCode)) {
            parts.add("适用场景：" + enumArray(extra.get("scenes"), CatalogEnums.SCENE));
            parts.add("合作商户范围：" + nodeText(extra.get("merchantScope")));
            parts.add("是否直接支付给商户：" + paidToMerchant(extra.get("paidToMerchant")));
        }
        return String.join("；", parts);
    }

    private static String years(JsonNode node) {
        return node == null || node.isNull() ? "暂无数据" : node.asInt() + " 年";
    }

    private static String months(JsonNode node) {
        return node == null || node.isNull() ? "暂无数据" : node.asInt() + " 个月";
    }

    private static String percent(JsonNode node) {
        return node == null || node.isNull() ? "暂无数据" : strip(node.decimalValue()) + "%";
    }

    private static String paidToMerchant(JsonNode node) {
        if (node == null || node.isNull()) {
            return "暂无数据";
        }
        return node.asBoolean() ? "是，款项直接支付给商户" : "否，款项发放给借款人";
    }

    private static String enumArray(JsonNode node, Map<String, String> dict) {
        if (node == null || node.isNull() || !node.isArray() || node.isEmpty()) {
            return "暂无数据";
        }
        List<String> names = new ArrayList<>();
        node.forEach(item -> names.add(dict.getOrDefault(item.asText(), item.asText())));
        return String.join("、", names);
    }

    private static String nodeText(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return "暂无数据";
        }
        return node.asText();
    }

    private static String joinNames(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "暂无数据";
        }
        return String.join("、", values);
    }

    private static String codesText(List<String> codes, Map<String, String> dict) {
        if (codes == null || codes.isEmpty()) {
            return "暂无数据";
        }
        return codes.stream().map(c -> dict.getOrDefault(c, c)).collect(Collectors.joining("、"));
    }

    private static String dictText(String code, Map<String, String> dict) {
        if (code == null || code.isBlank()) {
            return "暂无数据";
        }
        return dict.getOrDefault(code, "暂无数据");
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "暂无数据" : value;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String dateText(LocalDate date) {
        return date == null ? "暂无数据" : DATE.format(date);
    }

    private static String dateText(LocalDateTime time) {
        return time == null ? "暂无数据" : DATE.format(time.toLocalDate());
    }

    public static String dateTime(LocalDateTime time) {
        return time == null ? null : DATE_TIME.format(time);
    }

    public static String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
