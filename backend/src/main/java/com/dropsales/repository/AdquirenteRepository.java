package com.dropsales.repository;

import com.dropsales.model.Adquirente;
import com.dropsales.model.Loja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdquirenteRepository extends JpaRepository<Adquirente, Long> {
    List<Adquirente> findByLojaAndAtivoTrueOrderByNomeAsc(Loja loja);
    Optional<Adquirente> findByIdAndLoja(Long id, Loja loja);
    boolean existsByLojaAndNomeIgnoreCase(Loja loja, String nome);
}
