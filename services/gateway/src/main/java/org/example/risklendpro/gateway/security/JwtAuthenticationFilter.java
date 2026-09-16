package org.example.risklendpro.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${security.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublic(path, exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "请先登录");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();
            String role = claims.get("role", String.class);

            if (path.startsWith("/api/v1/admin/") && !"ADMIN".equals(role)) {
                return writeError(exchange, HttpStatus.FORBIDDEN, "无管理端访问权限");
            }

            // 下游服务各自校验 JWT 并从 SecurityContext 获取登录主体，网关无需转发身份头。
            return chain.filter(exchange);
        } catch (Exception ignored) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "token无效或已过期");
        }
    }

    private boolean isPublic(String path, HttpMethod method) {
        return method == HttpMethod.OPTIONS
                || path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/admin/login")
                || path.equals("/api/v1/admin/register")
                || path.equals("/api/v1/sync/blacklist")
                || path.startsWith("/platform/");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        byte[] body = ("{\"code\":" + status.value()
                + ",\"message\":\"" + message
                + "\",\"success\":false,\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
