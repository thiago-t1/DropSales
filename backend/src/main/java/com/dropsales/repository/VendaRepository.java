package com.dropsales.repository;

import com.dropsales.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findAllByOrderByCreatedAtDesc();

    List<Venda> findByUsuarioOrderByCreatedAtDesc(com.dropsales.model.Usuario usuario);

    List<Venda> findTop5ByUsuarioOrderByCreatedAtDesc(com.dropsales.model.Usuario usuario);

    @Query("SELECT v FROM Venda v WHERE v.createdAt >= :desde AND v.usuario = :usuario ORDER BY v.createdAt ASC")
    List<Venda> findVendasDesde(@Param("desde") LocalDateTime desde, @Param("usuario") com.dropsales.model.Usuario usuario);

    @Query("""
        SELECT p.nome, COALESCE(SUM(iv.quantidade), 0) as total
        FROM ItemVenda iv
        JOIN iv.produto p
        WHERE iv.venda.usuario = :usuario
        GROUP BY p.id, p.nome
        ORDER BY total DESC
        LIMIT 5
    """)
    List<Object[]> findTop5ProdutosPorQuantidade(@Param("usuario") com.dropsales.model.Usuario usuario);
}