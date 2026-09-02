package com.dropsales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ResumoRecebiveisResponse {
    private BigDecimal pendente;
    private BigDecimal recebido;
    private List<RecebivelResponse> recebiveis;
}
