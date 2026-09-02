package com.dropsales.repository;

import com.dropsales.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfiguracaoTaxaPagamentoRepository extends JpaRepository<ConfiguracaoTaxaPagamento, Long> {
    List<ConfiguracaoTaxaPagamento> findByLojaOrderByFormaPagamentoAscParcelasAsc(Loja loja);
    List<ConfiguracaoTaxaPagamento> findByLojaAndAtivoTrue(Loja loja);
    Optional<ConfiguracaoTaxaPagamento> findByIdAndLoja(Long id, Loja loja);
    boolean existsByLoja(Loja loja);
}
