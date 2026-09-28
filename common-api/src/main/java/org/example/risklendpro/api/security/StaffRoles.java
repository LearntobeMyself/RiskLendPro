package org.example.risklendpro.api.security;

import java.util.Set;

/**
 * 人员角色码。借款人只有 {@link #USER}；运营侧五码写入 admin.role 与 JWT。
 * 历史 JWT 中的 ADMIN / SUPER_ADMIN 归一为 {@link #SYS_ADMIN}。
 */
public final class StaffRoles {

    public static final String USER = "USER";
    public static final String RISK_MANAGER = "RISK_MANAGER";
    public static final String COLLECTOR = "COLLECTOR";
    public static final String AUDITOR = "AUDITOR";
    public static final String CS_AGENT = "CS_AGENT";
    public static final String SYS_ADMIN = "SYS_ADMIN";

    private static final Set<String> STAFF = Set.of(
            RISK_MANAGER, COLLECTOR, AUDITOR, CS_AGENT, SYS_ADMIN);

    private StaffRoles() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String value = role.trim().toUpperCase();
        if ("ADMIN".equals(value) || "SUPER_ADMIN".equals(value)) {
            return SYS_ADMIN;
        }
        return value;
    }

    public static boolean isStaff(String role) {
        return STAFF.contains(normalize(role));
    }

    public static boolean isBorrower(String role) {
        return USER.equals(normalize(role));
    }

    public static boolean isKnown(String role) {
        String value = normalize(role);
        return USER.equals(value) || STAFF.contains(value);
    }
}
