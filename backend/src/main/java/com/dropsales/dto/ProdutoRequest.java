package com.dropsales.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProdutoRequest {
    @NotBlank(message = "Nome e obrigatorio")
    private String nome;

    private String descricao;

    private String sku;

    @NotNull(message = "Preco de custo e obrigatorio")
    @DecimalMin(value = "0.0", message = "Preco de custo deve ser >= 0")
    private BigDecimal precoCusto;

    @NotNull(message = "Preco de venda e obrigatorio")
    @DecimalMin(value = "0.01", message = "Preco de venda deve ser > 0")
    private BigDecimal precoVenda;

    @NotNull(message = "Quantidade em estoque e obrigatoria")
    @Min(value = 0, message = "Estoque deve ser >= 0")
    private Integer quantidadeEstoque;

    @Min(value = 0, message = "Estoque minimo deve ser >= 0")
    private Integer estoqueMinimo = 5;

    private Long categoriaId;
}
