package com.dropsales.repository;

import com.dropsales.model.Transacao;
import com.dropsales.model.Venda;
import com.dropsales.model.Loja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    List<Transacao> findByVenda(Venda venda);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.tipo = 'RECEITA' AND t.status = 'PAGO' AND t.loja = :loja")
    BigDecimal somarReceitasPagas(@Param("loja") Loja loja);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.tipo = 'DESPESA' AND t.status = 'PAGO' AND t.loja = :loja")
    BigDecimal somarDespesasPagas(@Param("loja") Loja loja);

    /** Custo de Mercadoria Vendida: apenas despesas atreladas a vendas */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.tipo = 'DESPESA' AND t.status = 'PAGO' AND t.venda IS NOT NULL AND t.loja = :loja")
    BigDecimal somarCMV(@Param("loja") Loja loja);

    @Query("""
        SELECT t FROM Transacao t
        WHERE t.tipo = 'DESPESA' AND t.status = 'PAGO'
          AND t.venda IS NOT NULL
          AND t.loja = :loja
          AND t.createdAt >= :desde
        ORDER BY t.createdAt ASC
    """)
    List<Transacao> findCustosDesde(
            @Param("desde") OffsetDateTime desde,
            @Param("loja") Loja loja);

    /** Somente as duas colunas usadas no grafico; evita hidratar transacoes. */
    @Query("""
        SELECT t.createdAt, t.valor FROM Transacao t
        WHERE t.tipo = 'DESPESA' AND t.status = 'PAGO'
          AND t.venda IS NOT NULL
          AND t.loja = :loja
          AND t.createdAt >= :desde
        ORDER BY t.createdAt ASC
    """)
    List<Object[]> findTotaisCustosDesde(
            @Param("desde") OffsetDateTime desde,
            @Param("loja") Loja loja);
}
