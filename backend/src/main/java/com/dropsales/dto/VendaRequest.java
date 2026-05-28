package com.dropsales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class VendaRequest {
    private String observacao;

    @NotEmpty(message = "A venda deve ter pelo menos um item")
    @Valid
    private List<ItemVendaRequest> itens;
}
