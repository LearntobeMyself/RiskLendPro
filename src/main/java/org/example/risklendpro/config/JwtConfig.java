package org.example.risklendpro.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Configuration
public class JwtConfig {

    private static final String SECRET = "MySecretKeyForJWT2024VeryLongAndSafe12345678";
    private static final long EXPIRE = 7200;
    private static final String ROLE_CLAIM = "role";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String id) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + EXPIRE * 1000);

        return Jwts.builder()
                .subject(id)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(String id, String role) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + EXPIRE * 1000);

        return Jwts.builder()
                .subject(id)
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpireTime() {
        return EXPIRE;
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(ROLE_CLAIM, String.class);
    }
}