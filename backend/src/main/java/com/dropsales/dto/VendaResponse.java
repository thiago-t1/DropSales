package com.dropsales.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VendaResponse {
    private Long id;
    private String vendedor;
    private BigDecimal total;
    private String observacao;

    /** Serializado como ISO-8601 string: "2026-05-15T23:00:00" */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime criadoEm;

    private List<ItemResponse> itens;

    @Data
    @Builder
    public static class ItemResponse {
        private Long produtoId;
        private String produto;
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal subtotal;
    }
}