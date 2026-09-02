package com.dropsales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ItemVendaRequest {
    @NotNull(message = "Produto e obrigatorio")
    @Positive(message = "Produto deve ser um identificador positivo")
    private Long produtoId;

    @NotNull @Min(value = 1, message = "Quantidade minima e 1")
    private Integer quantidade;
}
