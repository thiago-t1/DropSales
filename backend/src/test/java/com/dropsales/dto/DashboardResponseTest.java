package com.dropsales.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardResponseTest {

    @Test
    void serializaAReceberComCamelCaseEsperadoPeloFrontend() throws Exception {
        DashboardResponse response = DashboardResponse.builder()
                .aReceber(new BigDecimal("57.60"))
                .build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.contains("\"aReceber\":57.60"));
        assertFalse(json.contains("\"areceber\""));
    }
}
