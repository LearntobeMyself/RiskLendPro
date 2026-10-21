package org.example.risklendpro.cs.security;

import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具：对身份证号、手机号、银行卡号打码。
 * 用于对话内容落库前 / 返回前兜底，避免敏感信息明文入库或外泄。
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    /** 18 位身份证：6 位地区 + 8 位生日 + 3 位顺序 + 1 位校验，保留前 6 后 4。 */
    private static final Pattern ID_CARD_18 =
            Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?!\\d)");
    /** 15 位身份证（老版）：保留前 6 后 3。 */
    private static final Pattern ID_CARD_15 =
            Pattern.compile("(?<!\\d)(\\d{6})\\d{6}(\\d{3})(?!\\d)");
    /** 手机号：1[3-9] + 9 位，保留前 3 后 4。 */
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(\\d{3})\\d{4}(\\d{4})(?!\\d)");
    /** 银行卡号：16-19 位连续数字，保留前 4 后 4。 */
    private static final Pattern BANK_CARD =
            Pattern.compile("(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");

    /**
     * 对文本中的身份证号、手机号、银行卡号打码，返回脱敏后的文本。
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = ID_CARD_18.matcher(text).replaceAll("$1********$2");
        result = ID_CARD_15.matcher(result).replaceAll("$1******$2");
        result = PHONE.matcher(result).replaceAll("$1****$2");
        result = BANK_CARD.matcher(result).replaceAll("$1 **** $2");
        return result;
    }

    /**
     * 是否包含敏感信息（身份证号 / 手机号 / 银行卡号）。
     */
    public static boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return ID_CARD_18.matcher(text).find()
                || ID_CARD_15.matcher(text).find()
                || PHONE.matcher(text).find()
                || BANK_CARD.matcher(text).find();
    }
}
