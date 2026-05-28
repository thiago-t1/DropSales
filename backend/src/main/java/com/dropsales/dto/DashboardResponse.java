package com.dropsales.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private BigDecimal receitas;
    private BigDecimal despesas;
    private BigDecimal saldo;
    private BigDecimal lucroLiquido;
    private BigDecimal cmv;
    private List<ProdutoResponse> estoqueBaixo;
    private List<VendaDiariaDTO> vendasDiarias;
    private List<VendaDiariaDTO> custosDiarios;
    private List<TopProdutoDTO> topProdutos;
    private List<VendaRecenteDTO> vendasRecentes;

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