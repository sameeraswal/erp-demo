package com.acme.erp.security;

import com.acme.erp.entity.User;
import com.acme.erp.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${erp.jwt.secret}") String secret,
            @Value("${erp.jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("erp.jwt.secret (JWT_SECRET) must be set");
        }
        this.key = Keys.hmacShaKeyFor(deriveKeyBytes(secret));
        this.expirationMs = expirationMs;
    }

    /** RFC 7518 requires >= 256 bits for HMAC-SHA; hash shorter secrets with SHA-256. */
    private static byte[] deriveKeyBytes(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= MIN_KEY_BYTES) {
            return bytes;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String createToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("tenant_id", user.getTenant().getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UUID getTenantId(Claims claims) {
        return UUID.fromString(claims.get("tenant_id", String.class));
    }

    public UserRole getRole(Claims claims) {
        return UserRole.valueOf(claims.get("role", String.class));
    }

    public String getEmail(Claims claims) {
        return claims.get("email", String.class);
    }
}
