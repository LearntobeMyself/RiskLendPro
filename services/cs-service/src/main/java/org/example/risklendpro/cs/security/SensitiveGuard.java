package org.example.risklendpro.cs.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 敏感内容识别（安全边界，置于大模型之外）：
 * 提示词注入、越权请求、内部规则/风控/黑名单、隐私数据（身份证/手机号/银行卡）。
 * 命中任一类型即走固定拒绝话术，杜绝提示词注入、客服越权或泄露敏感信息。
 *
 * <p>设计依据：提示词注入与越权不能只靠系统提示词防御，必须在模型调用之前、
 * 在代码层强制拦截（参考团队《AI客服开源项目调研总结》第六节安全边界链路）。
 */
@Component
public class SensitiveGuard {

    public static final String PROMPT_INJECTION = "PROMPT_INJECTION";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INTERNAL_RULE = "INTERNAL_RULE";
    public static final String PRIVACY_DATA = "PRIVACY_DATA";
    public static final String ABUSIVE = "ABUSIVE";

    /** 辱骂/违规内容：命中即拒绝回复并提醒文明用语，不进入大模型。 */
    private static final String[] ABUSIVE_KEYS = {
            "傻逼", "傻b", "傻B", "傻叉", "草泥马", "去死", "滚蛋", "垃圾客服",
            "废物", "白痴", "脑残", "智障", "贱人", "混蛋", "妈的", "卧槽",
            "我操", "操你", "去你", "死全家", "傻卵", "sb", "SB"
    };

    /** 提示词注入：要求忽略规则、输出系统提示词、角色扮演、越狱、导出全部数据等。 */
    private static final String[] PROMPT_INJECTION_KEYS = {
            "忽略之前", "忽略以上", "忽略上述", "忽略所有", "忽略规则", "忽略系统", "忽略指令",
            "无视之前", "无视以上", "无视上述", "忘记之前", "忘记规则",
            "输出你的系统", "输出系统提示", "显示系统提示", "你的系统提示词", "你的系统指令",
            "你的设定", "你的prompt", "你的 prompt", "系统提示词",
            "扮演", "角色扮演", "越狱", "jailbreak", "开发者模式", "假装",
            "查询所有用户", "所有用户数据", "所有订单", "导出所有", "所有客户", "全部用户数据"
    };
    private static final String[] UNAUTHORIZED_KEYS = {
            "别人的", "他人的", "其他人的", "查别人", "查他人", "查一下别人",
            "他的额度", "她的额度", "别人的额度", "他人额度", "帮我查他", "帮我查她"
    };
    /** 越权数据查询：查某人的额度/借款/评估等，且主语不是「我」。
     *  中间允许夹带手机号/用户名等（如「查一下 13800138002 这个用户的额度」）。 */
    private static final Pattern UNAUTHORIZED_QUERY = Pattern.compile(
            "(帮我查|查一下|查查|查询|看看|看下).{0,20}(的额度|的借款|的余额|的评估|的还款|的账单|借了多少|额度是多少)");
    /** 内部规则/风控：披露风控评分、规则、黑名单、征信、审批依据、通过率等一律代码级拒答。 */
    private static final String[] INTERNAL_RULE_KEYS = {
            "黑名单", "征信", "内部规则", "风控规则", "评分明细", "审批依据", "审批规则",
            "拒绝原因", "凭什么", "人工干预", "放水",
            "风控评分", "风控模型", "风控", "评分怎么算", "多少分能过", "多少分", "评分多少",
            "通过率", "过件率", "命中", "规则引擎", "模型分", "综合评分"
    };

    /**
     * 识别消息敏感类型，返回 null 表示不敏感。
     */
    public String detect(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        if (containsAny(message, PROMPT_INJECTION_KEYS)) {
            return PROMPT_INJECTION;
        }
        if (containsAny(message, ABUSIVE_KEYS)) {
            return ABUSIVE;
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
