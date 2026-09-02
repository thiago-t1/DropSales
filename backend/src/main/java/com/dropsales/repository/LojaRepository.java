package com.dropsales.repository;

import com.dropsales.model.Empresa;
import com.dropsales.model.Loja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LojaRepository extends JpaRepository<Loja, Long> {
    List<Loja> findByEmpresaAndAtivoTrueOrderByNomeAsc(Empresa empresa);
    Optional<Loja> findFirstByEmpresaAndAtivoTrueOrderByIdAsc(Empresa empresa);
    boolean existsByEmpresaAndNomeIgnoreCase(Empresa empresa, String nome);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Loja l WHERE l.id = :id")
    Optional<Loja> findByIdForUpdate(@Param("id") Long id);
}
