package com.dropsales.repository;

import com.dropsales.model.Empresa;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembroEmpresaRepository extends JpaRepository<MembroEmpresa, Long> {
    List<MembroEmpresa> findByUsuarioAndAtivoTrueOrderByIdAsc(Usuario usuario);
    List<MembroEmpresa> findByEmpresaOrderByUsuarioNomeAsc(Empresa empresa);
    Optional<MembroEmpresa> findByEmpresaAndUsuario(Empresa empresa, Usuario usuario);
    Optional<MembroEmpresa> findByIdAndEmpresa(Long id, Empresa empresa);
    boolean existsByUsuario(Usuario usuario);
}
