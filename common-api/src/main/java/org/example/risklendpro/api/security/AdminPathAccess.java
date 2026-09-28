package org.example.risklendpro.api.security;

/**
 * 网关与服务共用的路径鉴权。先映射路径到权限码，再套 {@link RolePermissions}。
 */
public final class AdminPathAccess {

    private AdminPathAccess() {
    }

    public static boolean allows(String rawPath, String httpMethod, String rawRole) {
        String path = normalizePath(rawPath);
        String role = StaffRoles.normalize(rawRole);
        boolean write = isWriteMethod(httpMethod) || isExport(path);

        if ("/admin/profile".equals(path)) {
            return StaffRoles.isStaff(role);
        }
        if (isBorrowerPath(path)) {
            return StaffRoles.USER.equals(role);
        }

        String permission = permissionFor(path);
        if (permission == null) {
            if (path.startsWith("/admin")) {
                return false;
            }
            return StaffRoles.isKnown(role);
        }
        return RolePermissions.allows(role, permission, write);
    }

    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        String path = rawPath;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.startsWith("/api/v1")) {
            path = path.substring("/api/v1".length());
        }
        if (path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    static boolean isWriteMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return false;
        }
        String method = httpMethod.toUpperCase();
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    private static boolean isExport(String path) {
        return path.endsWith("/export") || path.contains("/export/") || path.endsWith("/download");
    }

    private static boolean isBorrowerPath(String path) {
        return path.startsWith("/user")
                || path.startsWith("/risk/assessment")
                || path.startsWith("/loan")
                || path.equals("/repayment/plans")
                || path.equals("/repayment/execute")
                || path.startsWith("/repayment/record")
                || (path.startsWith("/cs") && !path.equals("/cs/status"));
    }

    static String permissionFor(String path) {
        if (path.equals("/repayment/statistics") || path.equals("/repayment/overdue")) {
            return Permissions.COLLECTION_CASE;
        }
        if (path.equals("/admin/register") || path.startsWith("/admin/system/admins")) {
            return Permissions.SYS_ADMIN;
        }
        if (path.startsWith("/admin/system/operation-logs") || path.startsWith("/admin/audit")) {
            return Permissions.AUDIT_READ;
        }
        if (path.startsWith("/admin/system/config") || path.startsWith("/admin/system/backups")) {
            return Permissions.SYS_CONFIG;
        }
        if (path.startsWith("/admin/users")) {
            return Permissions.SYS_USER;
        }
        if (path.startsWith("/admin/products")) {
            return Permissions.SYS_PRODUCT;
        }
        if (path.startsWith("/admin/channel")) {
            return Permissions.SYS_CHANNEL;
        }
        if (path.startsWith("/admin/cs/session")) {
            return Permissions.CS_SESSION;
        }
        if (path.startsWith("/admin/cs")) {
            return Permissions.CS_TICKET;
        }
        if (path.startsWith("/admin/collection")) {
            return path.contains("/action") || path.contains("/close")
                    ? Permissions.COLLECTION_ACTION
                    : Permissions.COLLECTION_CASE;
        }
        if (path.startsWith("/admin/repayment")) {
            return path.contains("overdue") ? Permissions.COLLECTION_CASE : Permissions.COLLECTION_ACTION;
        }
        if (path.startsWith("/admin/credit")) {
            return Permissions.CREDIT_ADJUST;
        }
        if (path.startsWith("/admin/loan") || path.equals("/admin/loan/pending-list")) {
            return Permissions.LOAN_APPROVE;
        }
        if (path.startsWith("/admin/bi") || path.equals("/admin/dashboard/stats")) {
            return Permissions.RISK_MONITOR;
        }
        if (path.startsWith("/admin/b-card")) {
            return Permissions.RISK_MONITOR;
        }
        if (path.startsWith("/admin/risk/anti-fraud") || path.startsWith("/admin/risk/multi-loan")) {
            return Permissions.RISK_FRAUD;
        }
        if (path.startsWith("/admin/risk") || path.startsWith("/admin/supplement")) {
            return Permissions.RISK_APPROVE;
        }
        return null;
    }
}
