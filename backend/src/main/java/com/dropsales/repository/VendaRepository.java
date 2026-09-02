package com.dropsales.repository;

import com.dropsales.model.Venda;
import com.dropsales.model.StatusVenda;
import com.dropsales.model.Loja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findByLojaOrderByCreatedAtDesc(Loja loja);

    Optional<Venda> findByIdAndLoja(Long id, Loja loja);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venda v WHERE v.id = :id AND v.loja = :loja")
    Optional<Venda> findByIdAndLojaForUpdate(
            @Param("id") Long id,
            @Param("loja") Loja loja);

    Optional<Venda> findByLojaAndIdempotencyKey(Loja loja, UUID idempotencyKey);

    // O vendedor vem junto; os itens permanecem lazy e são lidos dentro da
    // transação do Dashboard. Assim o TOP 5 continua sendo aplicado no banco.
    @EntityGraph(attributePaths = "usuario")
    List<Venda> findTop5ByLojaAndStatusOrderByCreatedAtDesc(
            Loja loja,
            StatusVenda status);

    @Query("""
        SELECT v FROM Venda v
        WHERE v.createdAt >= :desde
          AND v.loja = :loja
          AND v.status = 'CONCLUIDA'
        ORDER BY v.createdAt ASC
    """)
    List<Venda> findVendasDesde(
            @Param("desde") OffsetDateTime desde,
            @Param("loja") Loja loja);

    @Query("""
        SELECT p.nome, COALESCE(SUM(iv.quantidade), 0) as total
        FROM ItemVenda iv
        JOIN iv.produto p
        WHERE iv.venda.loja = :loja
          AND iv.venda.status = 'CONCLUIDA'
        GROUP BY p.id, p.nome
        ORDER BY total DESC
        LIMIT 5
    """)
    List<Object[]> findTop5ProdutosPorQuantidade(@Param("loja") Loja loja);
}
