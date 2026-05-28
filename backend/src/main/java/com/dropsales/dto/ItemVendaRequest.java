package com.dropsales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ItemVendaRequest {
    @NotNull(message = "Produto e obrigatorio")
    private Long produtoId;

    @NotNull @Min(value = 1, message = "Quantidade minima e 1")
    private Integer quantidade;
}
