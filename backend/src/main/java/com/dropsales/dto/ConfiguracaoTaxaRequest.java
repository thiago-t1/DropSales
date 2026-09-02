package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConfiguracaoTaxaRequest {
    @NotNull
    private FormaPagamento formaPagamento;

    @Positive(message = "Adquirente deve ser um identificador positivo")
    private Long adquirenteId;

    @Size(max = 40, message = "Bandeira deve ter no maximo 40 caracteres")
    private String bandeira;

    @NotNull
    @Min(value = 1, message = "Parcelas deve ser no minimo 1")
    @Max(value = 18, message = "Parcelas deve ser no maximo 18")
    private Integer parcelas;

    @NotNull
    @DecimalMin(value = "0.0000", message = "Taxa percentual nao pode ser negativa")
    @DecimalMax(value = "100.0000", message = "Taxa percentual nao pode exceder 100%")
    @Digits(integer = 3, fraction = 4,
            message = "Taxa percentual deve ter no maximo tres inteiros e quatro casas decimais")
    private BigDecimal taxaPercentual;

    @NotNull
    @DecimalMin(value = "0.00", message = "Taxa fixa nao pode ser negativa")
    @Digits(integer = 10, fraction = 2,
            message = "Taxa fixa deve ter no maximo dez inteiros e duas casas decimais")
    private BigDecimal taxaFixa;

    @NotNull
    @Min(value = 0, message = "Prazo de recebimento nao pode ser negativo")
    @Max(value = 365, message = "Prazo de recebimento deve ser no maximo 365 dias")
    private Integer prazoRecebimentoDias;

    @NotNull
    private Boolean ativo;
}
