package com.dropsales.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    @Test
    void tokenNovoUsaIdImutavelComoSubject() {
        JwtTokenProvider provider = new JwtTokenProvider(
                SECRET, 60_000, "dropsales-api", "dropsales-web");

        String token = provider.generateToken(42L);

        assertTrue(provider.validateToken(token));
        assertEquals("42", provider.getSubjectFromToken(token));
    }

    @Test
    void tokenComOutraAudienciaERejeitado() {
        JwtTokenProvider issuer = new JwtTokenProvider(
                SECRET, 60_000, "dropsales-api", "outro-cliente");
        JwtTokenProvider validator = new JwtTokenProvider(
                SECRET, 60_000, "dropsales-api", "dropsales-web");

        String token = issuer.generateToken(42L);

        assertFalse(validator.validateToken(token));
    }
}
