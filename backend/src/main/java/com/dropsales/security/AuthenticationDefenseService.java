package com.dropsales.security;

import com.dropsales.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de autenticacao por conta e origem.
 *
 * Esta implementacao atende uma unica instancia. Em ambiente com varias replicas,
 * configure um limitador compartilhado no gateway/Redis mantendo esta camada como
 * defesa adicional.
 */
@Service
public class AuthenticationDefenseService {

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;

    @Autowired
    public AuthenticationDefenseService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.lock-duration:PT15M}") Duration window) {
        this(maxAttempts, window, Clock.systemUTC());
    }

    AuthenticationDefenseService(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Configuracao de protecao de login invalida");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public void assertAllowed(String email, String remoteAddress) {
        Instant now = clock.instant();
        checkKey(accountKey(email), now);
        checkKey(accountOriginKey(email, remoteAddress), now);
    }

    public void recordFailure(String email, String remoteAddress) {
        Instant now = clock.instant();
        increment(accountKey(email), now);
        increment(accountOriginKey(email, remoteAddress), now);
        evictExpiredIfNeeded(now);
    }

    public void recordSuccess(String email) {
        attempts.remove(accountKey(email));
    }

    private void checkKey(String key, Instant now) {
        AttemptWindow state = attempts.get(key);
        if (state == null) {
            return;
        }
        if (!now.isBefore(state.blockedUntil())) {
            attempts.remove(key, state);
            return;
        }
        if (state.failures() >= maxAttempts) {
            long seconds = Duration.between(now, state.blockedUntil()).toSeconds();
            throw new TooManyRequestsException(
                    "Muitas tentativas. Aguarde antes de tentar novamente.",
                    seconds);
        }
    }

    private void increment(String key, Instant now) {
        attempts.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.blockedUntil())) {
                return new AttemptWindow(1, now.plus(window));
            }
            return new AttemptWindow(
                    Math.min(maxAttempts, current.failures() + 1),
                    current.blockedUntil());
        });
    }

    private void evictExpiredIfNeeded(Instant now) {
        if (attempts.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        attempts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().blockedUntil()));
        if (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.clear();
        }
    }

    private String accountKey(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "account:" + normalized;
    }

    private String accountOriginKey(String email, String remoteAddress) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalized = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress.trim();
        return "account-origin:" + normalizedEmail + ":" + normalized;
    }

    private record AttemptWindow(int failures, Instant blockedUntil) {
    }
}
