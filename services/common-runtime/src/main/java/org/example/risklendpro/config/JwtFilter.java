package org.example.risklendpro.config;

import org.example.risklendpro.common.security.JwtConfig;
import org.example.risklendpro.risk.blacklist.BlacklistSyncProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String SYNC_BLACKLIST_PATH = "/sync/blacklist";

    private final JwtConfig jwtConfig;
    private final BlacklistSyncProperties blacklistSyncProperties;

    @Autowired
    public JwtFilter(JwtConfig jwtConfig, BlacklistSyncProperties blacklistSyncProperties) {
        this.jwtConfig = jwtConfig;
        this.blacklistSyncProperties = blacklistSyncProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String requestURI = request.getRequestURI();

        if (isWhitelisted(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            sendError(response, 401, "请先登录");
            return;
        }

        if (isSyncBlacklistRequest(requestURI, request.getMethod())) {
            if (isValidSyncToken(token)) {
                setSyncAuthentication(request);
                chain.doFilter(request, response);
                return;
            }
            sendError(response, 401, "同步 Token 无效");
            return;
        }

        try {
            Claims claims = jwtConfig.parseToken(token);
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken authentication;
                if (role != null) {
                    authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
                } else {
                    authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, java.util.Collections.emptyList());
                }

                authentication.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            String attributeKey = "ADMIN".equals(role) ? "managerId" : "userId";
            request.setAttribute(attributeKey, userId);
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            sendError(response, 401, "token已过期，请重新登录");
        } catch (MalformedJwtException | SignatureException e) {
            sendError(response, 401, "token无效");
        } catch (Exception e) {
            sendError(response, 401, "认证失败");
        }
    }

    private boolean isSyncBlacklistRequest(String requestURI, String method) {
        return "POST".equalsIgnoreCase(method) && requestURI.endsWith(SYNC_BLACKLIST_PATH);
    }

    private boolean isValidSyncToken(String token) {
        String configuredToken = blacklistSyncProperties.getApiToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private void setSyncAuthentication(HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "sync", null,
                java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SYNC")));
        authentication.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean isWhitelisted(String requestURI) {
        return requestURI.contains("/auth/")
                || requestURI.startsWith("/internal/")
                || requestURI.contains("/internal/")
                || requestURI.contains("/admin/login")
                || requestURI.contains("/admin/register")
                || requestURI.contains("/swagger-ui")
                || requestURI.contains("/v3/api-docs")
                || requestURI.contains("/swagger-resources/")
                || requestURI.contains("/webjars/");
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void sendError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(String.format(
                "{\"code\": %d, \"message\": \"%s\", \"success\": false, \"data\": null}",
                code, message
        ));
        writer.flush();
        writer.close();
    }
}
