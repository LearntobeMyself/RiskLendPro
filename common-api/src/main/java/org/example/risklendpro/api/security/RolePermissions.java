package org.example.risklendpro.api.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色 → 固定权限集合。本学期不建权限表。
 */
public final class RolePermissions {

    private static final Map<String, Map<String, AccessLevel>> MATRIX = buildMatrix();

    private RolePermissions() {
    }

    public static AccessLevel access(String role, String permission) {
        Map<String, AccessLevel> row = MATRIX.get(StaffRoles.normalize(role));
        if (row == null || permission == null) {
            return AccessLevel.NONE;
        }
        return row.getOrDefault(permission, AccessLevel.NONE);
    }

    public static boolean allows(String role, String permission, boolean write) {
        AccessLevel level = access(role, permission);
        if (level == AccessLevel.NONE) {
            return false;
        }
        return write ? level == AccessLevel.WRITE : true;
    }

    /**
     * Spring Security 权威：{@code ROLE_角色码} + 有权的权限码；写权限另加 {@code 权限码:write}。
     */
    public static List<String> authorities(String role) {
        String normalized = StaffRoles.normalize(role);
        List<String> authorities = new ArrayList<>();
        if (normalized.isEmpty() || !StaffRoles.isKnown(normalized)) {
            return authorities;
        }
        authorities.add("ROLE_" + normalized);
        Map<String, AccessLevel> row = MATRIX.getOrDefault(normalized, Map.of());
        for (Map.Entry<String, AccessLevel> entry : row.entrySet()) {
            if (entry.getValue() == AccessLevel.NONE) {
                continue;
            }
            authorities.add(entry.getKey());
            if (entry.getValue() == AccessLevel.WRITE) {
                authorities.add(Permissions.write(entry.getKey()));
            }
        }
        return authorities;
    }

    private static Map<String, Map<String, AccessLevel>> buildMatrix() {
        Map<String, Map<String, AccessLevel>> matrix = new LinkedHashMap<>();
        matrix.put(StaffRoles.USER, row(
                Permissions.USER_PROFILE, AccessLevel.WRITE,
                Permissions.USER_ASSESS, AccessLevel.WRITE,
                Permissions.USER_LOAN, AccessLevel.WRITE,
                Permissions.USER_REPAY, AccessLevel.WRITE,
                Permissions.USER_CS, AccessLevel.WRITE
        ));
        matrix.put(StaffRoles.RISK_MANAGER, row(
                Permissions.RISK_RULE, AccessLevel.WRITE,
                Permissions.RISK_MODEL, AccessLevel.READ,
                Permissions.RISK_APPROVE, AccessLevel.WRITE,
                Permissions.RISK_FRAUD, AccessLevel.WRITE,
                Permissions.RISK_MONITOR, AccessLevel.WRITE,
                Permissions.LOAN_APPROVE, AccessLevel.WRITE,
                Permissions.CREDIT_ADJUST, AccessLevel.WRITE,
                Permissions.COLLECTION_CASE, AccessLevel.READ,
                Permissions.SYS_PRODUCT, AccessLevel.READ
        ));
        matrix.put(StaffRoles.COLLECTOR, row(
                Permissions.COLLECTION_CASE, AccessLevel.WRITE,
                Permissions.COLLECTION_ACTION, AccessLevel.WRITE
        ));
        matrix.put(StaffRoles.AUDITOR, row(
                Permissions.RISK_RULE, AccessLevel.READ,
                Permissions.RISK_MODEL, AccessLevel.READ,
                Permissions.RISK_APPROVE, AccessLevel.READ,
                Permissions.RISK_FRAUD, AccessLevel.READ,
                Permissions.RISK_MONITOR, AccessLevel.READ,
                Permissions.LOAN_APPROVE, AccessLevel.READ,
                Permissions.CREDIT_ADJUST, AccessLevel.READ,
                Permissions.COLLECTION_CASE, AccessLevel.READ,
                Permissions.COLLECTION_ACTION, AccessLevel.READ,
                Permissions.CS_SESSION, AccessLevel.READ,
                Permissions.CS_TICKET, AccessLevel.READ,
                Permissions.AUDIT_READ, AccessLevel.READ,
                Permissions.SYS_USER, AccessLevel.READ,
                Permissions.SYS_ADMIN, AccessLevel.READ,
                Permissions.SYS_CONFIG, AccessLevel.READ,
                Permissions.SYS_PRODUCT, AccessLevel.READ,
                Permissions.SYS_CHANNEL, AccessLevel.READ
        ));
        matrix.put(StaffRoles.CS_AGENT, row(
                Permissions.CS_SESSION, AccessLevel.READ,
                Permissions.CS_TICKET, AccessLevel.WRITE,
                Permissions.SYS_PRODUCT, AccessLevel.READ
        ));
        matrix.put(StaffRoles.SYS_ADMIN, row(
                Permissions.CREDIT_ADJUST, AccessLevel.READ,
                Permissions.AUDIT_READ, AccessLevel.READ,
                Permissions.SYS_USER, AccessLevel.WRITE,
                Permissions.SYS_ADMIN, AccessLevel.WRITE,
                Permissions.SYS_CONFIG, AccessLevel.WRITE,
                Permissions.SYS_PRODUCT, AccessLevel.WRITE,
                Permissions.SYS_CHANNEL, AccessLevel.WRITE
        ));
        return Map.copyOf(matrix);
    }

    private static Map<String, AccessLevel> row(Object... pairs) {
        Map<String, AccessLevel> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (AccessLevel) pairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}
