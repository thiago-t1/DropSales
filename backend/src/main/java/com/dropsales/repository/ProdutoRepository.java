package com.dropsales.repository;

import com.dropsales.model.Produto;
import com.dropsales.model.Loja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findByIdAndLoja(Long id, Loja loja);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE p.id = :id AND p.loja = :loja")
    Optional<Produto> findByIdAndLojaForUpdate(
            @Param("id") Long id,
            @Param("loja") Loja loja);
    List<Produto> findByLojaAndAtivoTrue(Loja loja);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE LOWER(p.sku) = LOWER(:sku) AND p.loja = :loja")
    Optional<Produto> findBySkuIgnoreCaseAndLojaForUpdate(
            @Param("sku") String sku,
            @Param("loja") Loja loja);

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidadeEstoque <= p.estoqueMinimo AND p.loja = :loja")
    List<Produto> findProdutosComEstoqueBaixo(@Param("loja") Loja loja);
}
