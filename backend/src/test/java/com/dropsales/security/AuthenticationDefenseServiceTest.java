package com.dropsales.security;

import com.dropsales.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationDefenseServiceTest {

    @Test
    void bloqueiaContaDepoisDoLimiteMesmoMudandoOrigem() {
        AuthenticationDefenseService service = new AuthenticationDefenseService(
                3,
                Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));

        service.recordFailure("user@example.com", "10.0.0.1");
        service.recordFailure("user@example.com", "10.0.0.2");
        service.recordFailure("user@example.com", "10.0.0.3");

        assertThrows(
                TooManyRequestsException.class,
                () -> service.assertAllowed("USER@example.com", "10.0.0.4"));
    }

    @Test
    void sucessoLimpaBloqueioDaConta() {
        AuthenticationDefenseService service = new AuthenticationDefenseService(
                2,
                Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));

        service.recordFailure("user@example.com", "10.0.0.1");
        service.recordFailure("user@example.com", "10.0.0.2");
        service.recordSuccess("user@example.com");

        assertDoesNotThrow(() -> service.assertAllowed("user@example.com", "10.0.0.3"));
    }
}
