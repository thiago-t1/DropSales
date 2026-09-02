package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import com.dropsales.model.StatusPagamentoVenda;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PagamentoVendaResponse {
    private Long id;
    private FormaPagamento formaPagamento;
    private Long adquirenteId;
    private String adquirenteNome;
    private String bandeira;
    private Integer parcelas;
    private BigDecimal valorBruto;
    private BigDecimal taxaPercentual;
    private BigDecimal taxaFixa;
    private BigDecimal taxaValor;
    private BigDecimal valorLiquido;
    private BigDecimal valorRecebido;
    private BigDecimal troco;
    private Integer prazoRecebimentoDias;
    private StatusPagamentoVenda status;
}
