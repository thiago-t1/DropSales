package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cabecalho de uma venda.
 */
@Entity
@Table(
        name = "vendas",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_vendas_loja_idempotency",
                columnNames = {"loja_id", "idempotency_key"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(
            name = "idempotency_request_hash",
            nullable = false,
            updatable = false,
            length = 64)
    private String idempotencyRequestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusVenda status = StatusVenda.CONCLUIDA;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    @Builder.Default
    private FormaPagamento formaPagamento = FormaPagamento.PIX;

    @Column(name = "taxa_pagamento_percentual", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxaPagamentoPercentual = BigDecimal.ZERO;

    @Column(name = "taxa_pagamento_valor", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxaPagamentoValor = BigDecimal.ZERO;

    @Column(name = "valor_liquido", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorLiquido = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelada_por_id")
    private Usuario canceladaPor;

    @Column(name = "cancelada_em")
    private OffsetDateTime canceladaEm;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ItemVenda> itens = new ArrayList<>();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<VendaAuditoria> auditorias = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prepararPersistencia() {
        if (idempotencyKey == null) {
            idempotencyKey = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            createdAt = createdAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
    }

    public void adicionarAuditoria(VendaAuditoria auditoria) {
        auditoria.setVenda(this);
        this.auditorias.add(auditoria);
    }
}
