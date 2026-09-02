package com.dropsales.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProdutoRequest {
    @NotBlank(message = "Nome e obrigatorio")
    @Size(max = 200, message = "Nome deve ter no maximo 200 caracteres")
    private String nome;

    @Size(max = 500, message = "Descricao deve ter no maximo 500 caracteres")
    private String descricao;

    @Size(max = 50, message = "SKU deve ter no maximo 50 caracteres")
    private String sku;

    @NotNull(message = "Preco de custo e obrigatorio")
    @DecimalMin(value = "0.0", message = "Preco de custo deve ser >= 0")
    @Digits(integer = 10, fraction = 2,
            message = "Preco de custo deve ter no maximo dez inteiros e duas casas decimais")
    private BigDecimal precoCusto;

    @NotNull(message = "Preco de venda e obrigatorio")
    @DecimalMin(value = "0.01", message = "Preco de venda deve ser > 0")
    @Digits(integer = 10, fraction = 2,
            message = "Preco de venda deve ter no maximo dez inteiros e duas casas decimais")
    private BigDecimal precoVenda;

    @NotNull(message = "Quantidade em estoque e obrigatoria")
    @Min(value = 0, message = "Estoque deve ser >= 0")
    private Integer quantidadeEstoque;

    @NotNull(message = "Estoque minimo e obrigatorio")
    @Min(value = 0, message = "Estoque minimo deve ser >= 0")
    private Integer estoqueMinimo = 5;

    @Positive(message = "Categoria deve ser um identificador positivo")
    private Long categoriaId;
}
