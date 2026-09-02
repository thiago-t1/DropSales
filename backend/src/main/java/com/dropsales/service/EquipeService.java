package com.dropsales.service;

import com.dropsales.dto.*;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.*;
import com.dropsales.repository.ConviteEmpresaRepository;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.MembroEmpresaRepository;
import com.dropsales.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TenantContextService tenantContext;
    private final MembroEmpresaRepository membroRepository;
    private final ConviteEmpresaRepository conviteRepository;
    private final LojaRepository lojaRepository;

    @Transactional(readOnly = true)
    public List<MembroEmpresaResponse> listarMembros() {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirAdministracao();
        return membroRepository.findByEmpresaOrderByUsuarioNomeAsc(contexto.empresa()).stream()
                .map(membro -> toMembroResponse(membro, contexto.usuario()))
                .toList();
    }

    @Transactional
    public MembroEmpresaResponse atualizarMembro(Long membroId, AtualizarMembroRequest request) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirAdministracao();
        MembroEmpresa membro = membroRepository.findByIdAndEmpresa(membroId, contexto.empresa())
                .orElseThrow(() -> new ResourceNotFoundException("Membro nao encontrado"));

        if (membro.getUsuario().getId().equals(contexto.usuario().getId()) && !request.getAtivo()) {
            throw new BusinessException("Voce nao pode remover o proprio acesso");
        }
        if (membro.getPapel() == PapelEmpresa.PROPRIETARIO
                && (request.getPapel() != PapelEmpresa.PROPRIETARIO || !request.getAtivo())) {
            throw new BusinessException("Transfira a propriedade antes de alterar o proprietario");
        }
        if (request.getPapel() == PapelEmpresa.PROPRIETARIO
                && membro.getPapel() != PapelEmpresa.PROPRIETARIO) {
            throw new BusinessException("A transferencia de propriedade exige um fluxo dedicado");
        }
        if ((membro.getPapel() == PapelEmpresa.ADMINISTRADOR
                || request.getPapel() == PapelEmpresa.ADMINISTRADOR)
                && contexto.membro().getPapel() != PapelEmpresa.PROPRIETARIO) {
            throw new BusinessException("Apenas o proprietario pode gerenciar administradores");
        }

        membro.setPapel(request.getPapel());
        membro.setAtivo(request.getAtivo());
        return toMembroResponse(membroRepository.save(membro), contexto.usuario());
    }

    @Transactional
    public ConviteEmpresaResponse criarConvite(ConviteEmpresaRequest request) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirAdministracao();
        if (request.getPapel() == PapelEmpresa.PROPRIETARIO) {
            throw new BusinessException("Convites nao podem conceder propriedade da empresa");
        }
        if (request.getPapel() == PapelEmpresa.ADMINISTRADOR
                && contexto.membro().getPapel() != PapelEmpresa.PROPRIETARIO) {
            throw new BusinessException("Apenas o proprietario pode convidar administradores");
        }
        String email = EmailNormalizer.normalize(request.getEmail());
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<ConviteEmpresa> pendentes = conviteRepository
                .findByEmpresaAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        contexto.empresa(),
                        email,
                        StatusConvite.PENDENTE);
        List<ConviteEmpresa> expirados = pendentes.stream()
                .filter(convite -> estaExpirado(convite, agora))
                .peek(convite -> convite.setStatus(StatusConvite.EXPIRADO))
                .toList();
        if (!expirados.isEmpty()) {
            conviteRepository.saveAll(expirados);
        }
        if (pendentes.stream().anyMatch(convite -> !estaExpirado(convite, agora))) {
            throw new BusinessException("Ja existe um convite pendente para este email");
        }
        boolean jaMembro = membroRepository.findByEmpresaOrderByUsuarioNomeAsc(contexto.empresa()).stream()
                .anyMatch(membro -> membro.getUsuario().getEmail().equalsIgnoreCase(email)
                        && Boolean.TRUE.equals(membro.getAtivo()));
        if (jaMembro) {
            throw new BusinessException("Este usuario ja faz parte da empresa");
        }

        String token = gerarToken();
        ConviteEmpresa convite = conviteRepository.save(ConviteEmpresa.builder()
                .empresa(contexto.empresa())
                .email(email)
                .papel(request.getPapel())
                .tokenHash(hashToken(token))
                .status(StatusConvite.PENDENTE)
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .convidadoPor(contexto.usuario())
                .build());

        return toConviteResponse(convite, token);
    }

    @Transactional
    public List<ConviteEmpresaResponse> listarConvitesPendentes() {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirAdministracao();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<ConviteEmpresa> convites = conviteRepository.findByEmpresaAndStatusOrderByCreatedAtDesc(
                contexto.empresa(), StatusConvite.PENDENTE);
        List<ConviteEmpresa> expirados = convites.stream()
                .filter(convite -> estaExpirado(convite, agora))
                .peek(convite -> convite.setStatus(StatusConvite.EXPIRADO))
                .toList();
        if (!expirados.isEmpty()) {
            conviteRepository.saveAll(expirados);
        }
        return convites.stream()
                .filter(convite -> convite.getStatus() == StatusConvite.PENDENTE)
                .map(convite -> toConviteResponse(convite, null))
                .toList();
    }

    @Transactional
    public void revogarConvite(Long conviteId) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirAdministracao();
        ConviteEmpresa convite = conviteRepository.findByIdAndEmpresa(conviteId, contexto.empresa())
                .orElseThrow(() -> new ResourceNotFoundException("Convite nao encontrado"));
        if (convite.getStatus() != StatusConvite.PENDENTE) {
            throw new BusinessException("Somente convites pendentes podem ser revogados");
        }
        convite.setStatus(StatusConvite.REVOGADO);
        conviteRepository.save(convite);
    }

    @Transactional(readOnly = true)
    public ConviteEmpresaResponse visualizarConvite(String token) {
        ConviteEmpresa convite = buscarConviteValido(token, false);
        ConviteEmpresaResponse response = toConviteResponse(convite, null);
        response.setEmail(mascararEmail(convite.getEmail()));
        return response;
    }

    @Transactional
    public ConviteEmpresaResponse aceitarConvite(AceitarConviteRequest request) {
        Usuario usuario = tenantContext.usuarioAtual();
        ConviteEmpresa convite = buscarConviteValido(request.getToken(), true);
        if (!convite.getEmail().equalsIgnoreCase(usuario.getEmail())) {
            throw new BusinessException("Entre com o mesmo email que recebeu o convite");
        }

        MembroEmpresa membro = membroRepository.findByEmpresaAndUsuario(convite.getEmpresa(), usuario)
                .orElseGet(() -> MembroEmpresa.builder()
                        .empresa(convite.getEmpresa())
                        .usuario(usuario)
                        .build());
        membro.setPapel(convite.getPapel());
        membro.setAtivo(true);
        membroRepository.save(membro);

        convite.setStatus(StatusConvite.ACEITO);
        convite.setAceitoPor(usuario);
        convite.setAcceptedAt(OffsetDateTime.now(ZoneOffset.UTC));
        conviteRepository.save(convite);
        return toConviteResponse(convite, null);
    }

    private ConviteEmpresa buscarConviteValido(String token, boolean bloquear) {
        ConviteEmpresa convite = (bloquear
                ? conviteRepository.findByTokenHashForUpdate(hashToken(token))
                : conviteRepository.findByTokenHash(hashToken(token)))
                .orElseThrow(() -> new ResourceNotFoundException("Convite invalido"));
        if (convite.getStatus() != StatusConvite.PENDENTE) {
            throw new BusinessException("Este convite nao esta mais disponivel");
        }
        if (estaExpirado(convite, OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException("Este convite expirou");
        }
        return convite;
    }

    private boolean estaExpirado(ConviteEmpresa convite, OffsetDateTime agora) {
        return !convite.getExpiresAt().isAfter(agora);
    }

    private String mascararEmail(String email) {
        int arroba = email.indexOf('@');
        int ponto = email.lastIndexOf('.');
        if (arroba <= 0 || ponto <= arroba + 1) {
            return "***";
        }
        String dominio = email.substring(arroba + 1, ponto);
        return email.charAt(0) + "***@"
                + dominio.charAt(0) + "***"
                + email.substring(ponto);
    }

    private MembroEmpresaResponse toMembroResponse(MembroEmpresa membro, Usuario atual) {
        return MembroEmpresaResponse.builder()
                .id(membro.getId())
                .usuarioId(membro.getUsuario().getId())
                .nome(membro.getUsuario().getNome())
                .email(membro.getUsuario().getEmail())
                .papel(membro.getPapel())
                .ativo(Boolean.TRUE.equals(membro.getAtivo()))
                .usuarioAtual(membro.getUsuario().getId().equals(atual.getId()))
                .desde(membro.getCreatedAt())
                .build();
    }

    private ConviteEmpresaResponse toConviteResponse(ConviteEmpresa convite, String token) {
        return ConviteEmpresaResponse.builder()
                .id(convite.getId())
                .empresaId(convite.getEmpresa().getId())
                .lojaId(lojaRepository
                        .findFirstByEmpresaAndAtivoTrueOrderByIdAsc(convite.getEmpresa())
                        .map(Loja::getId)
                        .orElse(null))
                .empresaNome(convite.getEmpresa().getNome())
                .email(convite.getEmail())
                .papel(convite.getPapel())
                .status(convite.getStatus())
                .expiraEm(convite.getExpiresAt())
                .criadoEm(convite.getCreatedAt())
                .token(token)
                .build();
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }
}
