package com.dropsales.controller;

import com.dropsales.exception.GlobalExceptionHandler;
import com.dropsales.service.VendaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VendaControllerTest {

    private VendaService vendaService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vendaService = mock(VendaService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VendaController(vendaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void retornaBadRequestParaFormaPagamentoDesconhecida() throws Exception {
        mockMvc.perform(post("/api/vendas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .content("""
                            {
                              "formaPagamento": "BOLETO",
                              "taxaPagamentoPercentual": 0,
                              "itens": [{"produtoId": 1, "quantidade": 1}]
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(vendaService);
    }

    @Test
    void retornaBadRequestQuandoIdempotencyKeyNaoFoiInformada() throws Exception {
        mockMvc.perform(post("/api/vendas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "formaPagamento": "PIX",
                              "taxaPagamentoPercentual": 0,
                              "itens": [{"produtoId": 1, "quantidade": 1}]
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(vendaService);
    }
}
