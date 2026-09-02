package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "lojas", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lojas_empresa_nome", columnNames = {"empresa_id", "nome"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Loja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

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
