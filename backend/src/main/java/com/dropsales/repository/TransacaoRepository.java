package com.dropsales.repository;

import com.dropsales.model.Transacao;
import com.dropsales.model.TipoTransacao;
import com.dropsales.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.dropsales.model.Usuario;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    List<Transacao> findByTipoAndUsuario(TipoTransacao tipo, Usuario usuario);

    List<Transacao> findByVenda(Venda venda);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.tipo = 'RECEITA' AND t.status = 'PAGO' AND t.usuario = :usuario")
    BigDecimal somarReceitasPagas(@Param("usuario") Usuario usuario);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.tipo = 'DESPESA' AND t.status = 'PAGO' AND t.usuario = :usuario")
    BigDecimal somarDespesasPagas(@Param("usuario") Usuario usuario);

    /** Custo de Mercadoria Vendida: apenas despesas atreladas a vendas */
    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM Transacao t WHERE t.tipo = 'DESPESA' AND t.status = 'PAGO' AND t.venda IS NOT NULL AND t.usuario = :usuario")
    BigDecimal somarCMV(@Param("usuario") Usuario usuario);

    /** Custos diarios (CMV) agrupados por data, a partir de uma data */
    @Query("""
        SELECT CAST(t.createdAt AS date), COALESCE(SUM(t.valor), 0)
        FROM Transacao t
        WHERE t.tipo = 'DESPESA' AND t.status = 'PAGO'
          AND t.venda IS NOT NULL
          AND t.usuario = :usuario
          AND t.createdAt >= :desde
        GROUP BY CAST(t.createdAt AS date)
        ORDER BY CAST(t.createdAt AS date) ASC
    """)
    List<Object[]> somarCustosDiariosDesde(@Param("desde") LocalDateTime desde, @Param("usuario") Usuario usuario);
}