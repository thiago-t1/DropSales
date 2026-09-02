package com.dropsales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DropSalesApplicationTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void rotaProtegidaSemSessaoRetorna401() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()"))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"));
    }
}
