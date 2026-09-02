package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/**
 * Entidade de Usuario do sistema DropSales.
 * Suporta perfis ADMIN e OPERADOR.
 */
@Entity
@Table(name = "usuarios")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Perfil perfil = Perfil.OPERADOR;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "foto_perfil", length = 5 * 1024 * 1024)
    private byte[] fotoPerfil;

    @Column(name = "foto_content_type", length = 50)
    private String fotoContentType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
