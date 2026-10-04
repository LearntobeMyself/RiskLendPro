package org.example.risklendpro.config;

import org.example.risklendpro.api.security.AdminPathAccess;
import org.example.risklendpro.api.security.StaffRoles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 直连服务端口时按路径再拦一层，避免只靠网关。
 */
@Component
public class AdminAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        if (hasAuthority(authentication, "ROLE_SYNC") || hasAuthority(authentication, "ROLE_INTERNAL")) {
            chain.doFilter(request, response);
            return;
        }

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
        if (!AdminPathAccess.allows(request.getRequestURI(), request.getMethod(), role)) {
            String message = StaffRoles.isBorrower(role) && request.getRequestURI().contains("/admin/")
                    ? "无管理端访问权限"
                    : "无权限访问该接口";
            sendForbidden(response, message);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write("{\"code\":403,\"message\":\"" + message + "\",\"success\":false,\"data\":null}");
        writer.flush();
        writer.close();
    }
}
