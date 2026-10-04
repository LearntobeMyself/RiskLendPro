package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.common.CatalogBusinessException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogEnums {

    private CatalogEnums() {
    }

    public static Map<String, String> named(String code, Map<String, String> dict) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String name = dict.get(code);
        if (name == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "非法枚举值: " + code);
        }
        return Map.of("code", code, "name", name);
    }

    public static List<Map<String, String>> options(Map<String, String> dict) {
        return dict.entrySet().stream()
                .map(e -> Map.of("code", e.getKey(), "name", e.getValue()))
                .toList();
    }

    public static void requireKnown(String code, Map<String, String> dict, String field) {
        if (code == null || code.isBlank()) {
            return;
        }
        if (!dict.containsKey(code)) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, field + "取值不合法");
        }
    }

    public static final Map<String, String> CATEGORY = ordered(
            "PERSONAL_CREDIT", "个人信用贷",
            "BUSINESS", "企业经营贷",
            "MORTGAGE", "抵押贷款",
            "CONSUMER_INSTALLMENT", "消费分期"
    );

    public static final Map<String, String> INSTITUTION_TYPE = ordered(
            "BANK", "银行",
            "CONSUMER_FINANCE", "消费金融公司",
            "MICRO_LOAN", "小额贷款公司",
            "AUTO_FINANCE", "汽车金融公司",
            "TRUST", "信托公司",
            "OTHER", "其他"
    );

    public static final Map<String, String> PRODUCT_STATUS = ordered(
            "DRAFT", "草稿",
            "ON_SHELF", "上架",
            "OFF_SHELF", "下架"
    );

    public static final Map<String, String> REPAYMENT_METHOD = ordered(
            "EQUAL_INSTALLMENT", "等额本息",
            "EQUAL_PRINCIPAL", "等额本金",
            "INTEREST_FIRST", "先息后本",
            "FLEXIBLE", "随借随还",
            "LUMP_SUM", "到期一次还本付息"
    );

    public static final Map<String, String> RATE_CALC = ordered(
            "SIMPLE", "单利",
            "IRR", "复利（内部收益率法）"
    );

    public static final Map<String, String> RATE_SCOPE = ordered(
            "ALL_IN", "已包含利息及相关费用",
            "INTEREST_ONLY", "仅为利息部分（是否另有费用见“其他费用”一项）"
    );

    public static final Map<String, String> RATE_SCOPE_CARD = ordered(
            "ALL_IN", "含费",
            "INTEREST_ONLY", "仅利息"
    );

    public static final Map<String, String> FEE_STATUS = ordered(
            "NONE", "机构明示无其他费用",
            "HAS", "有，见费用说明"
    );

    public static final Map<String, String> TERM_UNIT = ordered(
            "DAY", "天",
            "MONTH", "个月"
    );

    public static final Map<String, String> MORTGAGE = ordered(
            "NONE", "无需抵押",
            "REQUIRED", "需要抵押",
            "OPTIONAL", "可选"
    );

    public static final Map<String, String> IMAGE_TYPE = ordered(
            "COVER", "封面",
            "DETAIL", "详情",
            "INSTITUTION_LOGO", "机构Logo",
            "CATEGORY_ICON", "分类图标"
    );

    public static final Map<String, String> SORT_BY = ordered(
            "DEFAULT", "综合排序",
            "RATE_ASC", "利率从低到高（按录入数值）",
            "AMOUNT_DESC", "额度从高到低",
            "TERM_DESC", "期限从长到短",
            "NEWEST", "最新"
    );

    public static final Map<String, String> TARGET_GROUP = ordered(
            "SALARIED", "工薪族",
            "CIVIL_SERVANT", "公职人员",
            "PROVIDENT_FUND", "公积金缴存人群",
            "SOCIAL_SECURITY", "社保缴纳人群",
            "SELF_EMPLOYED", "个体工商户",
            "SME_OWNER", "小微企业主",
            "PROPERTY_OWNER", "有房人群",
            "CAR_OWNER", "有车人群",
            "GENERAL", "不限"
    );

    public static final Map<String, String> COLLATERAL = ordered(
            "HOUSE", "房产",
            "VEHICLE", "车辆",
            "OTHER", "其他"
    );

    public static final Map<String, String> SCENE = ordered(
            "DECORATION", "装修",
            "EDUCATION", "教育",
            "SHOPPING", "购物",
            "DIGITAL", "数码",
            "OTHER", "其他"
    );

    public static final List<String> BUSINESS_EXTRA_FIELDS = List.of(
            "minEstablishYears", "minOperatingMonths", "revenueRequirement", "licenseRequirement");
    public static final List<String> MORTGAGE_EXTRA_FIELDS = List.of(
            "collateralTypes", "collateralValueRequirement", "maxLoanToValue");
    public static final List<String> INSTALLMENT_EXTRA_FIELDS = List.of(
            "scenes", "merchantScope", "paidToMerchant");

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }

    public static List<String> extraFieldsOf(String categoryCode) {
        return switch (categoryCode) {
            case "BUSINESS" -> BUSINESS_EXTRA_FIELDS;
            case "MORTGAGE" -> MORTGAGE_EXTRA_FIELDS;
            case "CONSUMER_INSTALLMENT" -> INSTALLMENT_EXTRA_FIELDS;
            case "PERSONAL_CREDIT" -> List.of();
            default -> throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "分类不合法");
        };
    }

    public static boolean in(String code, String... values) {
        return Arrays.asList(values).contains(code);
    }
}
