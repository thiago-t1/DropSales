package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recebiveis", uniqueConstraints = {
        @UniqueConstraint(name = "uq_recebivel_pagamento_parcela", columnNames = {"pagamento_venda_id", "numero_parcela"})
}, indexes = {
        @Index(name = "idx_recebivel_loja_status_data", columnList = "loja_id,status,data_prevista")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Recebivel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagamento_venda_id", nullable = false)
    private PagamentoVenda pagamentoVenda;

    @Column(name = "numero_parcela", nullable = false)
    private Integer numeroParcela;

    @Column(name = "total_parcelas", nullable = false)
    private Integer totalParcelas;

    @Column(name = "valor_bruto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorBruto;

    @Column(name = "taxa_valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxaValor;

    @Column(name = "valor_liquido", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorLiquido;

    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusRecebivel status = StatusRecebivel.PENDENTE;

    @Column(name = "recebido_em")
    private OffsetDateTime recebidoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recebido_por_id")
    private Usuario recebidoPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
