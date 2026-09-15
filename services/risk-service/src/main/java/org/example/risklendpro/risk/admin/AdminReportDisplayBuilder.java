package org.example.risklendpro.risk.admin;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理端风控报告展示层：将 Redis/算分原始结构转为可读字段。
 */
@Component
public class AdminReportDisplayBuilder {

    private static final Pattern WOE_VALUE_PATTERN =
            Pattern.compile("^(.+?)(?:→|->)WOE=([+-]?\\d+(?:\\.\\d+)?)$");

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Set<String> INACTIVE_DUMMY_FEATURES = Set.of(
            "edu_mid", "edu_high", "house_owner", "own_realty", "job_stable", "employment_stable",
            "has_car_stated", "own_car", "marriage_married", "married"
    );

    private static final Map<String, String> ADMIN_LABELS = Map.ofEntries(
            Map.entry("ext_source_2", "第三方权威评分A"),
            Map.entry("ext_source_3", "第三方权威评分B"),
            Map.entry("gender_male", "性别"),
            Map.entry("edu_mid", "学历"),
            Map.entry("edu_high", "学历"),
            Map.entry("married", "婚姻状况"),
            Map.entry("marriage_married", "婚姻状况"),
            Map.entry("own_car", "是否有车"),
            Map.entry("has_car_stated", "是否有车"),
            Map.entry("own_realty", "是否有房"),
            Map.entry("house_owner", "是否有房"),
            Map.entry("employment_stable", "就业稳定性"),
            Map.entry("job_stable", "就业稳定性"),
            Map.entry("age_years", "年龄"),
            Map.entry("phone_change_days", "手机稳定性"),
            Map.entry("prev_refused_count", "历史被拒次数"),
            Map.entry("active_loans_count", "活跃贷款数"),
            Map.entry("credit_inquiry_1m", "近1月征信查询"),
            Map.entry("credit_inquiry_week", "近1周征信查询"),
            Map.entry("amt_income_total", "年收入"),
            Map.entry("amt_req_credit_bureau_mon", "近1月征信查询"),
            Map.entry("amt_req_credit_bureau_week", "近1周征信查询"),
            Map.entry("intercept", "基础评分")
    );

    private static final Map<String, String> OCCUPATION_LABELS = Map.ofEntries(
            Map.entry("Laborers", "体力劳动者"),
            Map.entry("Managers", "管理岗位"),
            Map.entry("Core staff", "核心员工"),
            Map.entry("Sales staff", "销售人员"),
            Map.entry("Accountants", "会计"),
            Map.entry("Drivers", "司机"),
            Map.entry("Cooking staff", "餐饮"),
            Map.entry("Cleaning staff", "保洁"),
            Map.entry("Private service staff", "私人服务"),
            Map.entry("High skill tech staff", "高技能技术"),
            Map.entry("Low skill Laborers", "低技能劳动"),
            Map.entry("Security staff", "安保"),
            Map.entry("Waiters/barmen staff", "服务员"),
            Map.entry("Medicine staff", "医疗"),
            Map.entry("Realty agents", "房产中介"),
            Map.entry("Secretaries", "文秘"),
            Map.entry("IT staff", "IT人员")
    );

    public Map<String, Object> build(Map<String, Object> report) {
        Map<String, Object> display = new LinkedHashMap<>();
        List<Map<String, Object>> scoreItems = buildScoreItems(report);
        display.put("scoreItems", scoreItems);
        display.put("scoreSummary", buildScoreSummary(scoreItems));
        display.put("externalItems", buildExternalItems(getMap(report, "externalFeatures")));
        display.put("userItems", buildUserItems(getMap(report, "userDetails")));
        return display;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildScoreItems(Map<String, Object> report) {
        Object raw = report.get("scoreDetails");
        if (!(raw instanceof List)) {
            return List.of();
        }
        Map<String, Object> user = getMap(report, "userDetails");
        Map<String, Object> ext = getMap(report, "externalFeatures");
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object row : (List<?>) raw) {
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> src = (Map<String, Object>) row;
            Map<String, Object> built = buildScoreItem(src, user, ext);
            if (built != null) {
                items.add(built);
            }
        }
        items.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> ((Number) m.get("sortWeight")).doubleValue()).reversed());
        return items;
    }

    private Map<String, Object> buildScoreItem(
            Map<String, Object> src, Map<String, Object> user, Map<String, Object> ext) {
        String featureCode = stringVal(src.get("feature"));
        String description = stringVal(src.get("description"));

        String rawValueText = stringVal(src.get("rawValueText"));
        Double woe = numberVal(src.get("woe"));
        if (rawValueText == null || woe == null) {
            ParsedValue parsed = parseValueField(src.get("value"));
            if (rawValueText == null) {
                rawValueText = parsed.rawValueText;
            }
            if (woe == null) {
                woe = parsed.woe;
            }
        }

        Double rawNumeric = parseRawNumeric(rawValueText);
        if (isInactiveDummy(featureCode, rawNumeric)) {
            return null;
        }

        double coefficient = numberVal(src.get("weight")) != null
                ? numberVal(src.get("weight"))
                : 0.0;
        double contribution = numberVal(src.get("contribution")) != null
                ? round2(numberVal(src.get("contribution")))
                : 0.0;
        String direction = contribution >= 0 ? "positive" : "negative";

        String label = resolveAdminLabel(featureCode, description);
        DisplayContext ctx = resolveDisplayContext(featureCode, rawValueText, rawNumeric, user, ext, description);
        String impactText = resolveImpactText(contribution);
        boolean showInAdmin = shouldShowInAdminTable(featureCode, contribution);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("featureCode", featureCode);
        item.put("displayValue", ctx.displayValue);
        item.put("meaning", ctx.meaning);
        item.put("impactText", impactText);
        item.put("contributionText", formatContributionText(contribution));
        item.put("category", ctx.category);
        item.put("showInAdminTable", showInAdmin);
        item.put("rawValueText", rawValueText != null ? rawValueText : "-");
        if (woe != null) {
            item.put("woe", round4(woe));
        }
        item.put("coefficient", round4(coefficient));
        item.put("contribution", contribution);
        item.put("direction", direction);
        item.put("sortWeight", Math.abs(contribution));
        return item;
    }

    private boolean isInactiveDummy(String featureCode, Double rawNumeric) {
        if (featureCode == null || rawNumeric == null) {
            return false;
        }
        return INACTIVE_DUMMY_FEATURES.contains(featureCode) && rawNumeric < 0.5;
    }

    private boolean shouldShowInAdminTable(String featureCode, double contribution) {
        if ("intercept".equals(featureCode)) {
            return false;
        }
        return Math.abs(contribution) >= 0.01;
    }

    private String resolveAdminLabel(String featureCode, String description) {
        if (featureCode != null && ADMIN_LABELS.containsKey(featureCode)) {
            return ADMIN_LABELS.get(featureCode);
        }
        if (featureCode != null && featureCode.startsWith("rule_bonus_")) {
            return description != null && !description.isBlank() ? description : "规则加成";
        }
        if (description != null && !description.isBlank()) {
            return description;
        }
        return featureCode != null ? featureCode : "-";
    }

    private DisplayContext resolveDisplayContext(
            String featureCode,
            String rawValueText,
            Double rawNumeric,
            Map<String, Object> user,
            Map<String, Object> ext,
            String description) {
        DisplayContext ctx = new DisplayContext();
        if (featureCode == null) {
            ctx.displayValue = humanizeFallback(rawValueText, rawNumeric);
            return ctx;
        }
        switch (featureCode) {
            case "ext_source_2" -> {
                ctx.displayValue = formatExtScore(ext != null ? ext.get("extSource2") : rawNumeric);
                ctx.meaning = "来自第三方征信机构的权威评分，数值越高表示信用越好，通常 0.6 以上较好";
                ctx.category = "征信";
            }
            case "ext_source_3" -> {
                ctx.displayValue = formatExtScore(ext != null ? ext.get("extSource3") : rawNumeric);
                ctx.meaning = "来自第三方征信机构的权威评分，数值越高表示信用越好，通常 0.6 以上较好";
                ctx.category = "征信";
            }
            case "gender_male" -> {
                ctx.displayValue = resolveGenderDisplay(user, rawNumeric);
                ctx.meaning = "客户性别";
                ctx.category = "基础";
            }
            case "edu_mid", "edu_high" -> {
                ctx.displayValue = user != null && user.get("education") != null
                        ? stringVal(user.get("education"))
                        : yesNoFromNumeric(rawNumeric);
                ctx.meaning = "客户最高学历，学历越高通常风险越低";
                ctx.category = "基础";
            }
            case "married", "marriage_married" -> {
                ctx.displayValue = user != null && user.get("marriage") != null
                        ? stringVal(user.get("marriage"))
                        : yesNoFromNumeric(rawNumeric);
                ctx.meaning = "婚姻状况，已婚通常视为更稳定";
                ctx.category = "基础";
            }
            case "own_car", "has_car_stated" -> {
                ctx.displayValue = user != null && user.get("hasCar") != null
                        ? (isTruthy(user.get("hasCar")) ? "是" : "否")
                        : yesNoFromNumeric(rawNumeric);
                ctx.meaning = "是否拥有车辆，有资产通常有助于评估还款能力";
                ctx.category = "资产";
            }
            case "own_realty", "house_owner" -> {
                ctx.displayValue = user != null && user.get("hasHouse") != null
                        ? (isTruthy(user.get("hasHouse")) ? "是" : "否")
                        : yesNoFromNumeric(rawNumeric);
                ctx.meaning = "是否拥有房产，有房产通常视为还款保障更强";
                ctx.category = "资产";
            }
            case "employment_stable", "job_stable" -> {
                ctx.displayValue = yesNoFromNumeric(rawNumeric);
                ctx.meaning = "就业是否稳定，稳定职业通常风险更低";
                ctx.category = "基础";
            }
            case "age_years" -> {
                if (user != null && user.get("age") != null) {
                    ctx.displayValue = user.get("age") + " 岁";
                } else if (rawNumeric != null) {
                    ctx.displayValue = Math.round(rawNumeric) + " 岁";
                } else {
                    ctx.displayValue = rawValueText != null ? rawValueText : "-";
                }
                ctx.meaning = "客户年龄，26–50 岁通常为较优区间";
                ctx.category = "基础";
            }
            case "phone_change_days" -> {
                int days = ext != null && ext.get("daysLastPhoneChange") != null
                        ? Math.abs(intVal(ext.get("daysLastPhoneChange")))
                        : (rawNumeric != null ? (int) Math.round(Math.abs(rawNumeric)) : 0);
                ctx.displayValue = days > 0 ? "约 " + days + " 天未换号" : "近期有换号";
                ctx.meaning = "手机号稳定程度，频繁换号可能风险偏高";
                ctx.category = "行为";
            }
            case "prev_refused_count" -> {
                int count = ext != null && ext.get("prevRefusedCount") != null
                        ? intVal(ext.get("prevRefusedCount"))
                        : (rawNumeric != null ? rawNumeric.intValue() : 0);
                ctx.displayValue = count + " 次";
                ctx.meaning = "历史被其他机构拒绝次数，越多通常风险越高";
                ctx.category = "风险";
            }
            case "active_loans_count" -> {
                int count = ext != null && ext.get("activeLoansCount") != null
                        ? intVal(ext.get("activeLoansCount"))
                        : (rawNumeric != null ? rawNumeric.intValue() : 0);
                ctx.displayValue = count + " 笔";
                ctx.meaning = "当前在还贷款笔数，笔数偏多可能多头借贷风险偏高";
                ctx.category = "负债";
            }
            case "credit_inquiry_1m", "amt_req_credit_bureau_mon" -> {
                int count = ext != null && ext.get("creditBureauMon") != null
                        ? intVal(ext.get("creditBureauMon"))
                        : (rawNumeric != null ? rawNumeric.intValue() : 0);
                ctx.displayValue = count + " 次";
                ctx.meaning = "近1月征信查询次数，查询过频可能资金紧张";
                ctx.category = "征信";
            }
            case "credit_inquiry_week", "amt_req_credit_bureau_week" -> {
                int count = ext != null && ext.get("creditBureauWeek") != null
                        ? intVal(ext.get("creditBureauWeek"))
                        : (rawNumeric != null ? rawNumeric.intValue() : 0);
                ctx.displayValue = count + " 次";
                ctx.meaning = "近1周征信查询次数，短期查询过频需关注";
                ctx.category = "征信";
            }
            case "amt_income_total" -> {
                BigDecimal income = ext != null ? decimalVal(ext.get("amtIncomeTotal")) : null;
                if (income == null && rawNumeric != null) {
                    income = BigDecimal.valueOf(rawNumeric);
                }
                ctx.displayValue = income != null ? formatWanYuan(income) + "/年" : humanizeFallback(rawValueText, rawNumeric);
                ctx.meaning = "推算年收入，用于评估还款能力";
                ctx.category = "收入";
            }
            case "intercept" -> {
                ctx.displayValue = "评分卡基础项";
                ctx.meaning = "模型内置基础分，不单独展示给管理员";
                ctx.category = "基础";
            }
            default -> {
                if (featureCode.startsWith("rule_bonus_")) {
                    ctx.displayValue = description != null ? description : humanizeFallback(rawValueText, rawNumeric);
                    ctx.meaning = "根据申请表信息给予的规则加成";
                    ctx.category = "基础";
                } else {
                    ctx.displayValue = humanizeFallback(rawValueText, rawNumeric);
                    ctx.meaning = null;
                    ctx.category = null;
                }
            }
        }
        if (ctx.displayValue == null || ctx.displayValue.isBlank()) {
            ctx.displayValue = "-";
        }
        return ctx;
    }

    private String resolveGenderDisplay(Map<String, Object> user, Double rawNumeric) {
        if (rawNumeric != null) {
            return rawNumeric >= 0.5 ? "男" : "女";
        }
        return "未知";
    }

    private String formatExtScore(Object val) {
        Double n = numberVal(val);
        if (n == null) {
            return "缺失";
        }
        return String.format("%.3f 分", n);
    }

    private String yesNoFromNumeric(Double rawNumeric) {
        if (rawNumeric == null) {
            return "-";
        }
        return rawNumeric >= 0.5 ? "是" : "否";
    }

    private String humanizeFallback(String rawValueText, Double rawNumeric) {
        if (rawValueText != null && !rawValueText.isBlank() && !"缺失".equals(rawValueText)) {
            if (rawNumeric != null && (rawNumeric == 0.0 || rawNumeric == 1.0)) {
                return yesNoFromNumeric(rawNumeric);
            }
            return rawValueText;
        }
        if (rawNumeric != null) {
            if (rawNumeric == 0.0 || rawNumeric == 1.0) {
                return yesNoFromNumeric(rawNumeric);
            }
            return formatNumber(rawNumeric);
        }
        return rawValueText != null ? rawValueText : "-";
    }

    private Double parseRawNumeric(String rawValueText) {
        if (rawValueText == null || rawValueText.isBlank() || "缺失".equals(rawValueText)) {
            return null;
        }
        try {
            return Double.parseDouble(rawValueText.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveImpactText(double contribution) {
        double abs = Math.abs(contribution);
        String degree;
        if (abs >= 1.0) {
            degree = "显著";
        } else if (abs >= 0.3) {
            degree = "中等";
        } else if (abs >= 0.01) {
            degree = "轻微";
        } else {
            return "几乎无影响";
        }
        return contribution >= 0 ? degree + "加分" : degree + "减分";
    }

    private String formatContributionText(double contribution) {
        if (contribution > 0) {
            return "+" + String.format("%.2f", contribution);
        }
        return String.format("%.2f", contribution);
    }

    private Map<String, Object> buildScoreSummary(List<Map<String, Object>> scoreItems) {
        double pos = 0;
        double neg = 0;
        for (Map<String, Object> item : scoreItems) {
            if (Boolean.FALSE.equals(item.get("showInAdminTable"))) {
                continue;
            }
            double c = ((Number) item.get("contribution")).doubleValue();
            if (c >= 0) {
                pos += c;
            } else {
                neg += c;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        double posRounded = round2(pos);
        double negRounded = round2(neg);
        summary.put("positiveContribution", posRounded);
        summary.put("negativeContribution", negRounded);
        summary.put("positiveText", "合计加分 " + String.format("%.2f", posRounded));
        summary.put("negativeText", "合计减分 " + String.format("%.2f", Math.abs(negRounded)));
        return summary;
    }

    private List<Map<String, Object>> buildExternalItems(Map<String, Object> ext) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (ext == null || ext.isEmpty()) {
            return items;
        }
        Boolean linked = boolVal(ext.get("linked"));
        if (linked != null) {
            items.add(item("第三方数据", linked ? "已关联" : "未关联", "基础", null));
        }
        if (ext.get("dataSource") != null) {
            items.add(item("数据来源", stringVal(ext.get("dataSource")), "基础", null));
        }
        if (ext.get("updatedAt") != null) {
            items.add(item("数据更新时间", formatUpdatedAt(ext.get("updatedAt")), "基础", null));
        }

        BigDecimal incomeTotal = decimalVal(ext.get("amtIncomeTotal"));
        if (incomeTotal != null) {
            items.add(item("年收入", formatWanYuan(incomeTotal), "收入", null));
            items.add(item("推算月薪", formatWanYuan(incomeTotal.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP)),
                    "收入", "年收入÷12，用于收入验真"));
        }

        if (ext.get("extSource2") != null) {
            items.add(item("外部权威分A", formatScore(ext.get("extSource2")), "征信", null));
        }
        if (ext.get("extSource3") != null) {
            items.add(item("外部权威分B", formatScore(ext.get("extSource3")), "征信", null));
        }
        if (ext.get("creditBureauWeek") != null) {
            items.add(item("近1周征信查询", ext.get("creditBureauWeek") + " 次", "征信", null));
        }
        if (ext.get("creditBureauMon") != null) {
            items.add(item("近1月征信查询", ext.get("creditBureauMon") + " 次", "征信", null));
        }

        if (ext.get("activeLoansCount") != null) {
            items.add(item("活跃贷款数", ext.get("activeLoansCount") + " 笔", "负债", null));
        }

        if (ext.get("target") != null) {
            items.add(item("历史违约", isTruthy(ext.get("target")) ? "有违约历史" : "无", "风险", null));
        }
        if (ext.get("prevRefusedCount") != null) {
            items.add(item("历史被拒次数", ext.get("prevRefusedCount") + " 次", "风险", null));
        }

        if (ext.get("daysLastPhoneChange") != null) {
            items.add(item("手机稳定性", "约 " + ext.get("daysLastPhoneChange") + " 天前换号", "行为", null));
        }

        Integer daysEmployed = intVal(ext.get("daysEmployed"));
        if (daysEmployed != null && daysEmployed != 0) {
            int years = Math.abs(daysEmployed) / 365;
            if (years > 0) {
                items.add(item("就业时长", "约 " + years + " 年", "基础", null));
            }
        }

        if (ext.get("occupationType") != null) {
            items.add(item("职业", mapOccupation(stringVal(ext.get("occupationType"))), "基础", null));
        }
        if (ext.get("educationType") != null) {
            items.add(item("学历", stringVal(ext.get("educationType")), "基础", null));
        }
        if (ext.get("flagOwnCar") != null) {
            items.add(item("有车", isTruthy(ext.get("flagOwnCar")) ? "是" : "否", "资产", null));
        }

        return items;
    }

    private List<Map<String, Object>> buildUserItems(Map<String, Object> user) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (user == null || user.isEmpty()) {
            return items;
        }
        addIfPresent(items, "姓名", user.get("name"));
        addIfPresent(items, "身份证", user.get("idCard"));
        addIfPresent(items, "年龄", user.get("age") != null ? user.get("age") + " 岁" : null);
        addIfPresent(items, "学历", user.get("education"));
        addIfPresent(items, "婚姻", user.get("marriage"));
        addIfPresent(items, "职业", user.get("jobType"));
        addIfPresent(items, "月收入(自填)", user.get("monthlyIncome"));
        if (user.get("hasHouse") != null) {
            items.add(item("有房", isTruthy(user.get("hasHouse")) ? "是" : "否", null, null));
        }
        if (user.get("hasCar") != null) {
            items.add(item("有车", isTruthy(user.get("hasCar")) ? "是" : "否", null, null));
        }
        return items;
    }

    private void addIfPresent(List<Map<String, Object>> items, String label, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            items.add(item(label, String.valueOf(value), null, null));
        }
    }

    private Map<String, Object> item(String label, String value, String group, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        if (group != null) {
            m.put("group", group);
        }
        if (hint != null) {
            m.put("hint", hint);
        }
        return m;
    }

    /** 为 scoreDetails 单行补充 rawValueText / woe（写入 Redis 或重算时使用）。 */
    public static void enrichDetailFields(Map<String, Object> detailMap) {
        if (detailMap.containsKey("rawValueText") && detailMap.containsKey("woe")) {
            return;
        }
        ParsedValue parsed = parseValueField(detailMap.get("value"));
        detailMap.putIfAbsent("rawValueText", parsed.rawValueText);
        if (parsed.woe != null) {
            detailMap.putIfAbsent("woe", parsed.woe);
        }
    }

    static ParsedValue parseValueField(Object valueObj) {
        ParsedValue parsed = new ParsedValue();
        if (valueObj == null) {
            parsed.rawValueText = "-";
            return parsed;
        }
        String value = String.valueOf(valueObj).trim();
        if ("缺失".equals(value)) {
            parsed.rawValueText = "缺失";
            return parsed;
        }
        Matcher matcher = WOE_VALUE_PATTERN.matcher(value);
        if (matcher.matches()) {
            parsed.rawValueText = matcher.group(1).trim();
            parsed.woe = Double.parseDouble(matcher.group(2));
            return parsed;
        }
        try {
            double num = Double.parseDouble(value);
            parsed.rawValueText = formatNumber(num);
            return parsed;
        } catch (NumberFormatException ignored) {
            parsed.rawValueText = value;
            return parsed;
        }
    }

    private String mapOccupation(String raw) {
        if (raw == null) {
            return "-";
        }
        return OCCUPATION_LABELS.getOrDefault(raw, raw);
    }

    private String formatWanYuan(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return amount.divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP) + " 万元";
    }

    private String formatScore(Object val) {
        Double n = numberVal(val);
        if (n == null) {
            return stringVal(val);
        }
        return String.format("%.3f", n);
    }

    private String formatUpdatedAt(Object val) {
        if (val == null) {
            return "-";
        }
        String s = String.valueOf(val);
        try {
            Instant instant = Instant.parse(s);
            return DISPLAY_TIME.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
        } catch (Exception ignored) {
            // fall through
        }
        try {
            long epoch = Long.parseLong(s);
            return DISPLAY_TIME.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()));
        } catch (Exception ignored) {
            return s;
        }
    }

    private static String formatNumber(double n) {
        if (n == Math.rint(n)) {
            return String.valueOf((long) n);
        }
        return String.format("%.4g", n);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> report, String key) {
        Object val = report.get(key);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return null;
    }

    private static boolean isTruthy(Object val) {
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(val)) || "1".equals(String.valueOf(val));
    }

    private static Boolean boolVal(Object val) {
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val == null) {
            return null;
        }
        return isTruthy(val);
    }

    private static Integer intVal(Object val) {
        Double n = numberVal(val);
        return n != null ? n.intValue() : null;
    }

    private static Double numberVal(Object val) {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        if (val == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimalVal(Object val) {
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        if (val instanceof Number) {
            return BigDecimal.valueOf(((Number) val).doubleValue());
        }
        if (val == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stringVal(Object val) {
        return val != null ? String.valueOf(val) : null;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    static class ParsedValue {
        String rawValueText;
        Double woe;
    }

    private static class DisplayContext {
        String displayValue;
        String meaning;
        String category;
    }
}
