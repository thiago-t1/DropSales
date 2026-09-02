package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import com.dropsales.model.StatusVenda;
import com.dropsales.model.TipoAuditoriaVenda;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VendaResponse {
    private Long id;
    private UUID idempotencyKey;
    private String vendedor;
    private StatusVenda status;
    private BigDecimal total;
    private FormaPagamento formaPagamento;
    private BigDecimal taxaPagamentoPercentual;
    private BigDecimal taxaPagamentoValor;
    private BigDecimal valorLiquido;
    private String observacao;
    private String motivoCancelamento;
    private String canceladaPor;
    private OffsetDateTime canceladaEm;

    /** Serializado em ISO-8601 com offset explicito, sempre normalizado em UTC. */
    private OffsetDateTime criadoEm;

    private List<ItemResponse> itens;
    private List<PagamentoVendaResponse> pagamentos;
    private List<AuditoriaResponse> auditorias;

    @Data
    @Builder
    public static class ItemResponse {
        private Long produtoId;
        private String produto;
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal subtotal;
    }

    @Data
    @Builder
    public static class AuditoriaResponse {
        private TipoAuditoriaVenda tipo;
        private String responsavel;
        private String descricao;
        private OffsetDateTime criadoEm;
    }
}
