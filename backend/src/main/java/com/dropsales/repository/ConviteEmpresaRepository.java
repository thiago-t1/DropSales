package com.dropsales.repository;

import com.dropsales.model.ConviteEmpresa;
import com.dropsales.model.Empresa;
import com.dropsales.model.StatusConvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConviteEmpresaRepository extends JpaRepository<ConviteEmpresa, Long> {
    Optional<ConviteEmpresa> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select convite from ConviteEmpresa convite where convite.tokenHash = :tokenHash")
    Optional<ConviteEmpresa> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<ConviteEmpresa> findByIdAndEmpresa(Long id, Empresa empresa);
    List<ConviteEmpresa> findByEmpresaAndStatusOrderByCreatedAtDesc(Empresa empresa, StatusConvite status);
    List<ConviteEmpresa> findByEmpresaAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
            Empresa empresa,
            String email,
            StatusConvite status);
}
