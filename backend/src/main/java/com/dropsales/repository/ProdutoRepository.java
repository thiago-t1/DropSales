package com.dropsales.repository;

import com.dropsales.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import com.dropsales.model.Usuario;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findByIdAndUsuario(Long id, Usuario usuario);
    List<Produto> findByUsuarioAndAtivoTrue(Usuario usuario);

    Optional<Produto> findBySkuAndUsuario(String sku, Usuario usuario);

    List<Produto> findAllByUsuario(Usuario usuario);

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidadeEstoque <= p.estoqueMinimo AND p.usuario = :usuario")
    List<Produto> findProdutosComEstoqueBaixo(@org.springframework.data.repository.query.Param("usuario") Usuario usuario);
}