package org.example.risklendpro.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

public class SecurityUtils {
    private static final String USER_ID_KEY = "userId";
    private static final String MANAGER_ID_KEY = "managerId";

    /** 从登录上下文解析当前用户 ID（首选 SecurityContext，兜底 request 属性） */
    public static Long getUserIdFromRequest(HttpServletRequest request) {
        return resolveId(request, USER_ID_KEY, "获取用户ID失败");
    }

    /** 从登录上下文解析当前管理员 ID（首选 SecurityContext，兜底 request 属性） */
    public static Long getManagerIdFromRequest(HttpServletRequest request) {
        return resolveId(request, MANAGER_ID_KEY, "获取管理员ID失败");
    }

    public static Long getUserId() {
        return resolvePrincipal("获取用户ID失败");
    }

    public static Long getManagerId() {
        return resolvePrincipal("获取管理员ID失败");
    }

    public static Long getAdminId() {
        return resolvePrincipal("获取管理员ID失败");
    }

    public static Long getAdminIdFromRequest(HttpServletRequest request) {
        return getManagerIdFromRequest(request);
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /** 获取当前登录主体 ID */
    public static Long getCurrentId() {
        return resolvePrincipal("获取当前登录ID失败");
    }

    private static Long resolveId(HttpServletRequest request, String attrKey, String errMsg) {
        Long fromPrincipal = principalId();
        if (fromPrincipal != null) {
            return fromPrincipal;
        }
        Object attr = request.getAttribute(attrKey);
        if (attr != null) {
            return Long.parseLong(attr.toString());
        }
        throw new RuntimeException(errMsg);
    }

    private static Long resolvePrincipal(String errMsg) {
        Long id = principalId();
        if (id != null) {
            return id;
        }
        throw new RuntimeException(errMsg);
    }

    private static Long principalId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return Long.parseLong(authentication.getPrincipal().toString());
        }
        return null;
    }
}
