package com.dropsales.repository;

import com.dropsales.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import com.dropsales.model.Usuario;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByNomeAndUsuario(String nome, Usuario usuario);
    Optional<Categoria> findByNomeIgnoreCaseAndUsuario(String nome, Usuario usuario);
    List<Categoria> findAllByUsuario(Usuario usuario);
}