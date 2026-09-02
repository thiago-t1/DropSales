package com.dropsales.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void autorizaHeadersUsadosPeloFrontendNoPreflight() {
        CorsConfig factory = new CorsConfig();
        ReflectionTestUtils.setField(factory, "allowedOriginsRaw",
                "https://thiago-t1.github.io, http://localhost:4200");
        CorsConfigurationSource source = factory.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/vendas");
        request.addHeader("Origin", "https://thiago-t1.github.io");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers",
                "authorization, content-type, x-loja-id, idempotency-key");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals("https://thiago-t1.github.io",
                configuration.checkOrigin("https://thiago-t1.github.io"));
        assertEquals(
                java.util.List.of("authorization", "content-type", "x-loja-id", "idempotency-key"),
                configuration.checkHeaders(
                        java.util.List.of(
                                "authorization",
                                "content-type",
                                "x-loja-id",
                                "idempotency-key")));
        assertFalse(Boolean.TRUE.equals(configuration.getAllowCredentials()));
        assertTrue(configuration.getExposedHeaders() == null
                || configuration.getExposedHeaders().isEmpty());
    }
}
