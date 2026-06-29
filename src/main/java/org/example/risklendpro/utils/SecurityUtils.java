package org.example.risklendpro.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

public class SecurityUtils {
    private static final String USER_ID_KEY = "userId";
    private static final String MANAGER_ID_KEY = "managerId";

    public static Long getUserIdFromRequest(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return Long.parseLong(authentication.getPrincipal().toString());
        }
        Object userIdObj = request.getAttribute(USER_ID_KEY);
        if (userIdObj != null) {
            return Long.parseLong(userIdObj.toString());
        }
        throw new RuntimeException("获取用户ID失败");
    }

    public static Long getManagerIdFromRequest(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return Long.parseLong(authentication.getPrincipal().toString());
        }
        Object managerIdObj = request.getAttribute(MANAGER_ID_KEY);
        if (managerIdObj != null) {
            return Long.parseLong(managerIdObj.toString());
        }
        throw new RuntimeException("获取管理员ID失败");
    }

    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return Long.parseLong(authentication.getPrincipal().toString());
        }
        throw new RuntimeException("获取用户ID失败");
    }

    public static Long getManagerId() {
        return getAdminId();
    }

    public static Long getAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return Long.parseLong(authentication.getPrincipal().toString());
        }
        throw new RuntimeException("获取管理员ID失败");
    }

    public static Long getAdminIdFromRequest(HttpServletRequest request) {
        return getManagerIdFromRequest(request);
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    public static Long getCurrentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            return Long.parseLong(authentication.getPrincipal().toString());
        }
        throw new RuntimeException("获取当前登录ID失败");
    }
}
