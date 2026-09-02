package com.dropsales.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protege endpoints publicos contra password spraying e criacao automatizada de
 * contas. Em producao com varias replicas, replique a regra no WAF/API gateway.
 */
@Component
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_TRACKED_ORIGINS = 20_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int loginRequestsPerMinute;
    private final int registerRequestsPerMinute;
    private final Clock clock;

    @Autowired
    public PublicEndpointRateLimitFilter(
            @Value("${app.security.rate-limit.login-per-minute:30}") int loginRequestsPerMinute,
            @Value("${app.security.rate-limit.register-per-minute:5}") int registerRequestsPerMinute) {
        this(loginRequestsPerMinute, registerRequestsPerMinute, Clock.systemUTC());
    }

    PublicEndpointRateLimitFilter(
            int loginRequestsPerMinute,
            int registerRequestsPerMinute,
            Clock clock) {
        this.loginRequestsPerMinute = positive(loginRequestsPerMinute);
        this.registerRequestsPerMinute = positive(registerRequestsPerMinute);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !"/api/auth/login".equals(path) && !"/api/auth/register".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = "/api/auth/register".equals(path)
                ? registerRequestsPerMinute
                : loginRequestsPerMinute;
        String key = path + ":" + normalizeAddress(request.getRemoteAddr());
        Instant now = clock.instant();
        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.resetAt())) {
                return new Window(1, now.plusSeconds(WINDOW_SECONDS));
            }
            return new Window(existing.count() + 1, existing.resetAt());
        });

        if (windows.size() > MAX_TRACKED_ORIGINS) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().resetAt()));
            if (windows.size() > MAX_TRACKED_ORIGINS) {
                windows.clear();
            }
        }

        if (current.count() > limit) {
            long retryAfter = Math.max(1, current.resetAt().getEpochSecond() - now.getEpochSecond());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Muitas requisicoes. Tente novamente em instantes.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int positive(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Limite de requisicoes deve ser positivo");
        }
        return value;
    }

    private String normalizeAddress(String address) {
        return address == null || address.isBlank() ? "unknown" : address.trim();
    }

    private record Window(int count, Instant resetAt) {
    }
}
