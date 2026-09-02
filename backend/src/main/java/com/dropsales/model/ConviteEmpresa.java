package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "convites_empresa", indexes = {
        @Index(name = "idx_convite_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_convite_empresa_status", columnList = "empresa_id,status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConviteEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 200)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PapelEmpresa papel;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusConvite status = StatusConvite.PENDENTE;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "convidado_por_id", nullable = false)
    private Usuario convidadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aceito_por_id")
    private Usuario aceitoPor;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
