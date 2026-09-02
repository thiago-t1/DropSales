package com.dropsales.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade de Produto com controle de estoque.
 */
@Entity
@Table(
        name = "produtos",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_produtos_loja_sku",
                columnNames = {"loja_id", "sku"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 50)
    private String sku;

    @Column(name = "preco_custo", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precoCusto = BigDecimal.ZERO;

    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "quantidade_estoque", nullable = false)
    @Builder.Default
    private Integer quantidadeEstoque = 0;

    @Column(name = "estoque_minimo", nullable = false)
    @Builder.Default
    private Integer estoqueMinimo = 5;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Verifica se o estoque esta abaixo do minimo */
    public boolean isEstoqueBaixo() {
        return quantidadeEstoque != null && estoqueMinimo != null
                && quantidadeEstoque <= estoqueMinimo;
    }
}
