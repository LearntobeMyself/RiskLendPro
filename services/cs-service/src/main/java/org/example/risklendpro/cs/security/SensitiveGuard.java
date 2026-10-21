package org.example.risklendpro.cs.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 敏感内容识别：越权请求、内部规则/风控/黑名单、隐私数据（身份证/手机号/银行卡）。
 * 命中任一类型即走固定拒绝话术，杜绝客服越权或泄露敏感信息。
 */
@Component
public class SensitiveGuard {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INTERNAL_RULE = "INTERNAL_RULE";
    public static final String PRIVACY_DATA = "PRIVACY_DATA";

    private static final String[] UNAUTHORIZED_KEYS = {
            "别人的", "他人的", "其他人的", "查别人", "查他人", "查一下别人",
            "他的额度", "她的额度", "别人的额度", "他人额度", "帮我查他", "帮我查她"
    };
    /** 越权数据查询：查某人的额度/借款/评估等，且主语不是「我」。 */
    private static final Pattern UNAUTHORIZED_QUERY = Pattern.compile(
            "(帮我查|查一下|查查|查询|看看).{0,10}(的额度|的借款|的余额|的评估|的还款|的账单|借了多少|额度是多少)");
    private static final String[] INTERNAL_RULE_KEYS = {
            "黑名单", "征信", "内部规则", "风控规则", "评分明细", "审批依据",
            "拒绝原因", "凭什么", "人工干预", "放水"
    };

    /**
     * 识别消息敏感类型，返回 null 表示不敏感。
     */
    public String detect(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        if (containsAny(message, UNAUTHORIZED_KEYS)) {
            return UNAUTHORIZED;
        }
        if (UNAUTHORIZED_QUERY.matcher(message).find() && !message.contains("我的")) {
            return UNAUTHORIZED;
        }
        if (containsAny(message, INTERNAL_RULE_KEYS)) {
            return INTERNAL_RULE;
        }
        if (SensitiveDataMasker.containsSensitive(message)) {
            return PRIVACY_DATA;
        }
        return null;
    }

    private boolean containsAny(String message, String[] keys) {
        for (String key : keys) {
            if (message.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
