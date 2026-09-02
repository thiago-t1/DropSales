package com.dropsales.service;

import com.dropsales.dto.EmpresaRequest;
import com.dropsales.dto.ContextoResponse;
import com.dropsales.dto.LojaRequest;
import com.dropsales.exception.BusinessException;
import com.dropsales.model.*;
import com.dropsales.repository.EmpresaRepository;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.MembroEmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final LojaRepository lojaRepository;
    private final MembroEmpresaRepository membroRepository;
    private final TenantContextService tenantContext;

    @Transactional
    public ContextoResponse.EmpresaResumo criarEmpresa(EmpresaRequest request) {
        Usuario usuario = tenantContext.usuarioAtual();
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nome(request.getNome().trim())
                .documento(normalizarDocumento(request.getDocumento()))
                .ativo(true)
                .build());
        String nomeLoja = request.getNomeLoja() == null || request.getNomeLoja().isBlank()
                ? "Loja principal" : request.getNomeLoja().trim();
        lojaRepository.save(Loja.builder()
                .empresa(empresa)
                .nome(nomeLoja)
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build());
        membroRepository.save(MembroEmpresa.builder()
                .empresa(empresa)
                .usuario(usuario)
                .papel(PapelEmpresa.PROPRIETARIO)
                .ativo(true)
                .build());
        return resumo(empresa, PapelEmpresa.PROPRIETARIO);
    }

    @Transactional
    public ContextoResponse.EmpresaResumo atualizarEmpresa(EmpresaRequest request) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirAdministracao();
        Empresa empresa = contexto.empresa();
        empresa.setNome(request.getNome().trim());
        empresa.setDocumento(normalizarDocumento(request.getDocumento()));
        empresaRepository.save(empresa);
        return resumo(empresa, contexto.membro().getPapel());
    }

    @Transactional
    public ContextoResponse.LojaResumo criarLoja(LojaRequest request) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirAdministracao();
        if (lojaRepository.existsByEmpresaAndNomeIgnoreCase(contexto.empresa(), request.getNome().trim())) {
            throw new BusinessException("Ja existe uma loja com este nome na empresa");
        }
        String timezone = validarTimezone(request.getTimezone());
        Loja loja = lojaRepository.save(Loja.builder()
                .empresa(contexto.empresa())
                .nome(request.getNome().trim())
                .timezone(timezone)
                .ativo(true)
                .build());
        return ContextoResponse.LojaResumo.builder()
                .id(loja.getId()).nome(loja.getNome()).timezone(loja.getTimezone()).build();
    }

    private ContextoResponse.EmpresaResumo resumo(Empresa empresa, PapelEmpresa papel) {
        return ContextoResponse.EmpresaResumo.builder()
                .id(empresa.getId())
                .nome(empresa.getNome())
                .documento(empresa.getDocumento())
                .papel(papel)
                .lojas(lojaRepository.findByEmpresaAndAtivoTrueOrderByNomeAsc(empresa).stream()
                        .map(item -> ContextoResponse.LojaResumo.builder()
                                .id(item.getId()).nome(item.getNome()).timezone(item.getTimezone()).build())
                        .toList())
                .build();
    }

    private String normalizarDocumento(String documento) {
        if (documento == null || documento.isBlank()) return null;
        return documento.replaceAll("[^0-9A-Za-z]", "");
    }

    private String validarTimezone(String timezone) {
        String valor = timezone == null || timezone.isBlank()
                ? "America/Sao_Paulo"
                : timezone.trim();
        try {
            ZoneId.of(valor);
            return valor;
        } catch (DateTimeException ex) {
            throw new BusinessException("Fuso horario invalido");
        }
    }
}
