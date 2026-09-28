package org.example.risklendpro.api.security;

/**
 * 权限码，与《综设III身份与权限对照》一致。写操作另发 {@code 权限码:write} 权威。
 */
public final class Permissions {

    public static final String WRITE_SUFFIX = ":write";

    public static final String USER_PROFILE = "user:profile";
    public static final String USER_ASSESS = "user:assess";
    public static final String USER_LOAN = "user:loan";
    public static final String USER_REPAY = "user:repay";
    public static final String USER_CS = "user:cs";

    public static final String RISK_RULE = "risk:rule";
    public static final String RISK_MODEL = "risk:model";
    public static final String RISK_APPROVE = "risk:approve";
    public static final String RISK_FRAUD = "risk:fraud";
    public static final String RISK_MONITOR = "risk:monitor";

    public static final String LOAN_APPROVE = "loan:approve";
    public static final String CREDIT_ADJUST = "credit:adjust";

    public static final String COLLECTION_CASE = "collection:case";
    public static final String COLLECTION_ACTION = "collection:action";

    public static final String CS_SESSION = "cs:session";
    public static final String CS_TICKET = "cs:ticket";

    public static final String AUDIT_READ = "audit:read";

    public static final String SYS_USER = "sys:user";
    public static final String SYS_ADMIN = "sys:admin";
    public static final String SYS_CONFIG = "sys:config";
    public static final String SYS_PRODUCT = "sys:product";
    public static final String SYS_CHANNEL = "sys:channel";

    private Permissions() {
    }

    public static String write(String permission) {
        return permission + WRITE_SUFFIX;
    }
}
