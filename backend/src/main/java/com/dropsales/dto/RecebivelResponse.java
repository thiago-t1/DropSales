package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import com.dropsales.model.StatusRecebivel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class RecebivelResponse {
    private Long id;
    private Long vendaId;
    private FormaPagamento formaPagamento;
    private String adquirente;
    private Integer numeroParcela;
    private Integer totalParcelas;
    private BigDecimal valorBruto;
    private BigDecimal taxaValor;
    private BigDecimal valorLiquido;
    private LocalDate dataPrevista;
    private StatusRecebivel status;
    private OffsetDateTime recebidoEm;
}
