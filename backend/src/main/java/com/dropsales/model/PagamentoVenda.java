package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pagamentos_venda", indexes = {
        @Index(name = "idx_pagamento_venda", columnList = "venda_id"),
        @Index(name = "idx_pagamento_loja", columnList = "loja_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PagamentoVenda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 24)
    private FormaPagamento formaPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adquirente_id")
    private Adquirente adquirente;

    @Column(length = 40)
    private String bandeira;

    @Column(nullable = false)
    @Builder.Default
    private Integer parcelas = 1;

    @Column(name = "valor_bruto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorBruto;

    @Column(name = "taxa_percentual", nullable = false, precision = 7, scale = 4)
    @Builder.Default
    private BigDecimal taxaPercentual = BigDecimal.ZERO;

    @Column(name = "taxa_fixa", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxaFixa = BigDecimal.ZERO;

    @Column(name = "taxa_valor", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxaValor = BigDecimal.ZERO;

    @Column(name = "valor_liquido", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorLiquido;

    @Column(name = "valor_recebido", precision = 12, scale = 2)
    private BigDecimal valorRecebido;

    @Column(precision = 12, scale = 2)
    private BigDecimal troco;

    @Column(name = "prazo_recebimento_dias", nullable = false)
    @Builder.Default
    private Integer prazoRecebimentoDias = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusPagamentoVenda status = StatusPagamentoVenda.ATIVO;

    @Column(name = "substituido_em")
    private OffsetDateTime substituidoEm;

    @Column(name = "cancelado_em")
    private OffsetDateTime canceladoEm;
}
