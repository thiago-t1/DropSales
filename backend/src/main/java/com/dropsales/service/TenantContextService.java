package com.dropsales.service;

import com.dropsales.dto.ContextoResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ForbiddenException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.*;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.MembroEmpresaRepository;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.SecurityUtils;
import com.dropsales.util.EmailNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantContextService {

    public static final String LOJA_HEADER = "X-Loja-Id";

    private final UsuarioRepository usuarioRepository;
    private final MembroEmpresaRepository membroRepository;
    private final LojaRepository lojaRepository;
    private final TenantProvisioningService provisioningService;
    private final HttpServletRequest request;

    public record ContextoAtual(Usuario usuario, Empresa empresa, Loja loja, MembroEmpresa membro) {}

    @Transactional
    public ContextoAtual atual() {
        Usuario usuario = usuarioAtual();
        List<MembroEmpresa> membros = membroRepository.findByUsuarioAndAtivoTrueOrderByIdAsc(usuario);

        if (membros.isEmpty()) {
            if (membroRepository.existsByUsuario(usuario)) {
                throw new ForbiddenException("Seu acesso a empresa esta suspenso");
            }
            Usuario usuarioBloqueado = usuarioRepository.findByEmailIgnoreCaseForUpdate(usuario.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Usuario nao encontrado"));
            membros = membroRepository.findByUsuarioAndAtivoTrueOrderByIdAsc(
                    usuarioBloqueado);
            if (membros.isEmpty()) {
                if (membroRepository.existsByUsuario(usuarioBloqueado)) {
                    throw new ForbiddenException("Seu acesso a empresa esta suspenso");
                }
                provisioningService.criarEstruturaInicial(usuarioBloqueado, null);
                membros = membroRepository.findByUsuarioAndAtivoTrueOrderByIdAsc(
                        usuarioBloqueado);
            }
            usuario = usuarioBloqueado;
        }
        membros = membros.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEmpresa().getAtivo()))
                .toList();
        if (membros.isEmpty()) {
            throw new BusinessException("Voce nao possui acesso a uma empresa ativa");
        }

        Long lojaSolicitada = lojaIdDoHeader();
        if (lojaSolicitada != null) {
            Loja loja = lojaRepository.findById(lojaSolicitada)
                    .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                    .filter(item -> Boolean.TRUE.equals(item.getEmpresa().getAtivo()))
                    .orElseThrow(() -> new ResourceNotFoundException("Loja nao encontrada"));
            MembroEmpresa membro = membros.stream()
                    .filter(item -> item.getEmpresa().getId().equals(loja.getEmpresa().getId()))
                    .findFirst()
                    .orElseThrow(() -> new ForbiddenException("Voce nao possui acesso a esta loja"));
            return new ContextoAtual(usuario, loja.getEmpresa(), loja, membro);
        }

        List<Loja> lojasAcessiveis = membros.stream()
                .flatMap(item -> lojaRepository
                        .findByEmpresaAndAtivoTrueOrderByNomeAsc(item.getEmpresa())
                        .stream())
                .toList();
        if (lojasAcessiveis.isEmpty()) {
            throw new BusinessException("A empresa nao possui uma loja ativa");
        }
        if (lojasAcessiveis.size() > 1 && requisicaoMutavel()) {
            throw new BusinessException(
                    "Informe X-Loja-Id para realizar operacoes em uma conta com varias lojas");
        }
        Loja loja = lojasAcessiveis.get(0);
        MembroEmpresa membro = membros.stream()
                .filter(item -> item.getEmpresa().getId()
                        .equals(loja.getEmpresa().getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Voce nao possui acesso a esta loja"));
        return new ContextoAtual(usuario, membro.getEmpresa(), loja, membro);
    }

    public Usuario usuarioAtual() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            throw new BusinessException("Usuario nao autenticado");
        }
        return usuarioRepository.findByEmailIgnoreCase(EmailNormalizer.normalize(email))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado"));
    }

    @Transactional
    public ContextoResponse obterContexto() {
        ContextoAtual atual = atual();
        List<MembroEmpresa> membros = membroRepository
                .findByUsuarioAndAtivoTrueOrderByIdAsc(atual.usuario()).stream()
                .filter(membro -> Boolean.TRUE.equals(membro.getEmpresa().getAtivo()))
                .toList();

        List<ContextoResponse.EmpresaResumo> empresas = membros.stream()
                .map(membro -> ContextoResponse.EmpresaResumo.builder()
                        .id(membro.getEmpresa().getId())
                        .nome(membro.getEmpresa().getNome())
                        .documento(membro.getEmpresa().getDocumento())
                        .papel(membro.getPapel())
                        .lojas(lojaRepository.findByEmpresaAndAtivoTrueOrderByNomeAsc(membro.getEmpresa()).stream()
                                .map(loja -> ContextoResponse.LojaResumo.builder()
                                        .id(loja.getId())
                                        .nome(loja.getNome())
                                        .timezone(loja.getTimezone())
                                        .build())
                                .toList())
                        .build())
                .toList();

        return ContextoResponse.builder()
                .usuarioId(atual.usuario().getId())
                .usuarioNome(atual.usuario().getNome())
                .usuarioEmail(atual.usuario().getEmail())
                .empresaAtualId(atual.empresa().getId())
                .lojaAtualId(atual.loja().getId())
                .papelAtual(atual.membro().getPapel())
                .empresas(empresas)
                .build();
    }

    public ContextoAtual exigirGerencia() {
        ContextoAtual contexto = atual();
        if (!List.of(PapelEmpresa.PROPRIETARIO, PapelEmpresa.ADMINISTRADOR, PapelEmpresa.GERENTE)
                .contains(contexto.membro().getPapel())) {
            throw new ForbiddenException("Seu perfil nao possui permissao para esta operacao");
        }
        return contexto;
    }

    public ContextoAtual exigirAdministracao() {
        ContextoAtual contexto = atual();
        if (!List.of(PapelEmpresa.PROPRIETARIO, PapelEmpresa.ADMINISTRADOR)
                .contains(contexto.membro().getPapel())) {
            throw new ForbiddenException("Apenas proprietarios e administradores podem realizar esta operacao");
        }
        return contexto;
    }

    private Long lojaIdDoHeader() {
        String value = request.getHeader(LOJA_HEADER);
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException("Identificador de loja invalido");
        }
    }

    private boolean requisicaoMutavel() {
        String method = request.getMethod();
        return method != null && List.of("POST", "PUT", "PATCH", "DELETE")
                .contains(method.toUpperCase());
    }
}
