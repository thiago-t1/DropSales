package com.dropsales.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Provedor de tokens JWT para autenticacao stateless.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expiration;
    private final String issuer;
    private final String audience;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expiration,
            @Value("${app.jwt.issuer:dropsales-api}") String issuer,
            @Value("${app.jwt.audience:dropsales-web}") String audience) {
        if (expiration < 60_000 || expiration > 86_400_000) {
            throw new IllegalArgumentException("Expiracao JWT deve estar entre 1 minuto e 24 horas");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateToken(Long usuarioId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .header().type("JWT").and()
                .signWith(key)
                .compact();
    }

    public String getSubjectFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
