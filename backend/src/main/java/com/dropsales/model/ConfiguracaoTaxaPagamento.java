package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "configuracoes_taxa_pagamento", indexes = {
        @Index(name = "idx_taxa_pagamento_loja_forma", columnList = "loja_id,forma_pagamento")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConfiguracaoTaxaPagamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "taxa_percentual", nullable = false, precision = 7, scale = 4)
    @Builder.Default
    private BigDecimal taxaPercentual = BigDecimal.ZERO;

    @Column(name = "taxa_fixa", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxaFixa = BigDecimal.ZERO;

    @Column(name = "prazo_recebimento_dias", nullable = false)
    @Builder.Default
    private Integer prazoRecebimentoDias = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
