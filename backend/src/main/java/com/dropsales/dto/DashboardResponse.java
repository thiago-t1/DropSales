package com.dropsales.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private BigDecimal receitas;
    private BigDecimal receitaBruta;
    private BigDecimal despesas;
    /** Alias legado de saldoOperacional. */
    private BigDecimal saldo;
    private BigDecimal saldoOperacional;
    private BigDecimal lucroLiquido;
    private BigDecimal lucroBruto;
    private BigDecimal cmv;
    private BigDecimal taxasPagamento;
    private BigDecimal recebidoLiquido;
    private BigDecimal aReceber;
    private List<ProdutoResponse> estoqueBaixo;
    private List<VendaDiariaDTO> vendasDiarias;
    private List<VendaDiariaDTO> custosDiarios;
    private List<TopProdutoDTO> topProdutos;
    private List<VendaRecenteDTO> vendasRecentes;

    @JsonProperty("aReceber")
    public BigDecimal getAReceber() {
        return aReceber;
    }

    @JsonProperty("aReceber")
    public void setAReceber(BigDecimal aReceber) {
        this.aReceber = aReceber;
    }

    @Data
    @Builder
    public static class VendaDiariaDTO {
        private String data;
        private BigDecimal total;
    }

    @Data
    @Builder
    public static class TopProdutoDTO {
        private String nome;
        private Long totalUnidades;
    }

    @Data
    @Builder
    public static class VendaRecenteDTO {
        private Long id;
        private String vendedor;
        private String data;
        private BigDecimal valor;
        private Integer totalItens;
    }
}
