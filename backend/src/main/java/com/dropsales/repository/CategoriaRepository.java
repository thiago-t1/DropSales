package com.dropsales.repository;

import com.dropsales.model.Categoria;
import com.dropsales.model.Loja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByNomeIgnoreCaseAndLoja(String nome, Loja loja);
    Optional<Categoria> findByIdAndLoja(Long id, Loja loja);
}
