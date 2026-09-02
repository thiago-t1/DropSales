package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "vendas_auditoria",
        indexes = @Index(name = "idx_vendas_auditoria_venda", columnList = "venda_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendaAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAuditoriaVenda tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Usuario responsavel;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void definirTimestampUtc() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            createdAt = createdAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
    }
}
