package com.dropsales.repository;

import com.dropsales.model.Loja;
import com.dropsales.model.PagamentoVenda;
import com.dropsales.model.StatusPagamentoVenda;
import com.dropsales.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PagamentoVendaRepository extends JpaRepository<PagamentoVenda, Long> {
    List<PagamentoVenda> findByVendaOrderByIdAsc(Venda venda);
    List<PagamentoVenda> findByVendaAndStatusOrderByIdAsc(
            Venda venda,
            StatusPagamentoVenda status);
    List<PagamentoVenda> findByVendaAndStatusNotOrderByIdAsc(
            Venda venda,
            StatusPagamentoVenda status);
    List<PagamentoVenda> findByLoja(Loja loja);

    @Query("""
        SELECT COALESCE(SUM(p.taxaValor), 0)
        FROM PagamentoVenda p
        WHERE p.loja = :loja AND p.status = 'ATIVO'
    """)
    BigDecimal somarTaxasAtivasPorLoja(@Param("loja") Loja loja);
}
