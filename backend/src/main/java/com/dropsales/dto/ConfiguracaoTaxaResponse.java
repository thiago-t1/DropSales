package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ConfiguracaoTaxaResponse {
    private Long id;
    private FormaPagamento formaPagamento;
    private Long adquirenteId;
    private String adquirenteNome;
    private String bandeira;
    private Integer parcelas;
    private BigDecimal taxaPercentual;
    private BigDecimal taxaFixa;
    private Integer prazoRecebimentoDias;
    private boolean ativo;
}
