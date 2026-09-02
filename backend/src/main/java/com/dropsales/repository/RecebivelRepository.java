package com.dropsales.repository;

import com.dropsales.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RecebivelRepository extends JpaRepository<Recebivel, Long> {
    List<Recebivel> findByLojaOrderByDataPrevistaAscIdAsc(Loja loja);
    List<Recebivel> findByPagamentoVenda(PagamentoVenda pagamentoVenda);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recebivel r WHERE r.id = :id AND r.loja = :loja")
    Optional<Recebivel> findByIdAndLojaForUpdate(
            @Param("id") Long id,
            @Param("loja") Loja loja);

    @Query("SELECT COALESCE(SUM(r.valorLiquido), 0) FROM Recebivel r WHERE r.loja = :loja AND r.status = :status")
    BigDecimal somarLiquidoPorStatus(@Param("loja") Loja loja, @Param("status") StatusRecebivel status);
}
