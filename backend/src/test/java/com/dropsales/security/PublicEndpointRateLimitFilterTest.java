package com.dropsales.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PublicEndpointRateLimitFilterTest {

    @Test
    void bloqueiaExcessoDeCadastrosPorOrigem() throws Exception {
        PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter(
                30,
                2,
                Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int attempt = 0; attempt < 3; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
            request.setRemoteAddr("203.0.113.10");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            if (attempt == 2) {
                assertEquals(429, response.getStatus());
                assertEquals("60", response.getHeader("Retry-After"));
            }
        }

        verify(chain, times(2)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
