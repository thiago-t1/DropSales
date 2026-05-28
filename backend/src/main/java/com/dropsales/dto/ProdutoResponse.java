package com.dropsales.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ProdutoResponse {
    private Long id;
    private String nome;
    private String sku;
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private Integer quantidadeEstoque;
    private Integer estoqueMinimo;
    private String categoria;
    private boolean estoqueBaixo;
}