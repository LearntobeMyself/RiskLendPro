package org.example.risklendpro.config;

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

@Component
public class JwtFilter extends OncePerRequestFilter {
    
    private final JwtConfig jwtConfig;
    
    @Autowired
    public JwtFilter(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // 1. 处理 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 获取请求路径
        String requestURI = request.getRequestURI();

        // 3. 白名单路径直接放行（不检查 token）
        if (isWhitelisted(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // 4. 提取并验证 token
        String token = extractToken(request);
        if (token == null) {
            sendError(response, 401, "请先登录");
            return;
        }

        // 5. 解析 token
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

    /**
     * 判断请求路径是否在白名单中（不需要 token）
     */
    private boolean isWhitelisted(String requestURI) {
        return requestURI.contains("/auth/")
                || requestURI.contains("/swagger-ui")
                || requestURI.contains("/v3/api-docs")
                || requestURI.contains("/swagger-resources/")
                || requestURI.contains("/webjars/");
    }
    
    /**
     * 从请求头中提取 token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * 统一错误响应
     */
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