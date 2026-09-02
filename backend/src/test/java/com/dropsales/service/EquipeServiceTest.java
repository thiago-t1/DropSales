package com.dropsales.service;

import com.dropsales.dto.AceitarConviteRequest;
import com.dropsales.dto.ConviteEmpresaRequest;
import com.dropsales.dto.ConviteEmpresaResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ForbiddenException;
import com.dropsales.model.ConviteEmpresa;
import com.dropsales.model.Empresa;
import com.dropsales.model.Loja;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.StatusConvite;
import com.dropsales.model.Usuario;
import com.dropsales.repository.ConviteEmpresaRepository;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.MembroEmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipeServiceTest {

    @Mock private TenantContextService tenantContext;
    @Mock private MembroEmpresaRepository membroRepository;
    @Mock private ConviteEmpresaRepository conviteRepository;
    @Mock private LojaRepository lojaRepository;

    private EquipeService service;
    private Empresa empresa;
    private Usuario proprietario;
    private MembroEmpresa membroProprietario;
    private Loja loja;
    private Usuario convidado;
    private ConviteEmpresa convite;

    @BeforeEach
    void setUp() {
        service = new EquipeService(
                tenantContext,
                membroRepository,
                conviteRepository,
                lojaRepository);
        empresa = Empresa.builder()
                .id(10L)
                .nome("Loja Teste")
                .ativo(true)
                .build();
        proprietario = Usuario.builder()
                .id(1L)
                .nome("Proprietario")
                .email("dono@teste.com")
                .build();
        convidado = Usuario.builder()
                .id(2L)
                .nome("Convidado")
                .email("equipe@teste.com")
                .build();
        membroProprietario = MembroEmpresa.builder()
                .id(11L)
                .empresa(empresa)
                .usuario(proprietario)
                .papel(PapelEmpresa.PROPRIETARIO)
                .ativo(true)
                .build();
        loja = Loja.builder()
                .id(12L)
                .empresa(empresa)
                .nome("Principal")
                .ativo(true)
                .build();
        convite = ConviteEmpresa.builder()
                .id(20L)
                .empresa(empresa)
                .email(convidado.getEmail())
                .papel(PapelEmpresa.GERENTE)
                .tokenHash("hash")
                .status(StatusConvite.PENDENTE)
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .convidadoPor(proprietario)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    @Test
    void aceitarConviteUsaBloqueioEAtivaMembroDaEmpresa() {
        AceitarConviteRequest request = request("token-seguro");
        when(tenantContext.usuarioAtual()).thenReturn(convidado);
        when(conviteRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(convite));
        when(membroRepository.findByEmpresaAndUsuario(empresa, convidado))
                .thenReturn(Optional.empty());
        when(membroRepository.save(any(MembroEmpresa.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conviteRepository.save(convite)).thenReturn(convite);
        when(lojaRepository.findFirstByEmpresaAndAtivoTrueOrderByIdAsc(empresa))
                .thenReturn(Optional.of(loja));

        ConviteEmpresaResponse response = service.aceitarConvite(request);

        assertEquals(StatusConvite.ACEITO, response.getStatus());
        assertEquals(PapelEmpresa.GERENTE, response.getPapel());
        assertEquals(empresa.getId(), response.getEmpresaId());
        assertEquals(loja.getId(), response.getLojaId());
        assertEquals(convidado, convite.getAceitoPor());
        assertNotNull(convite.getAcceptedAt());
        verify(conviteRepository).findByTokenHashForUpdate(anyString());
        verify(conviteRepository, never()).findByTokenHash(anyString());
        verify(membroRepository).save(any(MembroEmpresa.class));
    }

    @Test
    void conviteSoPodeSerAceitoPeloEmailDestinatario() {
        Usuario outroUsuario = Usuario.builder()
                .id(3L)
                .nome("Outro")
                .email("outro@teste.com")
                .build();
        when(tenantContext.usuarioAtual()).thenReturn(outroUsuario);
        when(conviteRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(convite));

        assertThrows(
                BusinessException.class,
                () -> service.aceitarConvite(request("token-seguro")));

        verify(membroRepository, never()).save(any());
        verify(conviteRepository, never()).save(any());
    }

    @Test
    void visualizacaoPublicaMascaraEmailDoDestinatario() {
        when(conviteRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(convite));
        when(lojaRepository.findFirstByEmpresaAndAtivoTrueOrderByIdAsc(empresa))
                .thenReturn(Optional.of(loja));

        ConviteEmpresaResponse response = service.visualizarConvite("token-seguro");

        assertEquals("e***@t***.com", response.getEmail());
        assertEquals(empresa.getNome(), response.getEmpresaNome());
    }

    @Test
    void listarMembrosExigePapelDeProprietarioOuAdministrador() {
        when(tenantContext.exigirAdministracao())
                .thenThrow(new ForbiddenException("Acesso negado"));

        assertThrows(ForbiddenException.class, service::listarMembros);

        verifyNoInteractions(membroRepository);
    }

    @Test
    void conviteExpiradoNaoBloqueiaNovoConviteParaMesmoEmail() {
        convite.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ConviteEmpresaRequest request = new ConviteEmpresaRequest();
        request.setEmail(" EQUIPE@TestE.COM ");
        request.setPapel(PapelEmpresa.GERENTE);
        TenantContextService.ContextoAtual contexto =
                new TenantContextService.ContextoAtual(
                        proprietario,
                        empresa,
                        loja,
                        membroProprietario);

        when(tenantContext.exigirAdministracao()).thenReturn(contexto);
        when(conviteRepository
                .findByEmpresaAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        empresa,
                        "equipe@teste.com",
                        StatusConvite.PENDENTE))
                .thenReturn(List.of(convite));
        when(membroRepository.findByEmpresaOrderByUsuarioNomeAsc(empresa))
                .thenReturn(List.of());
        when(conviteRepository.save(any(ConviteEmpresa.class)))
                .thenAnswer(invocation -> {
                    ConviteEmpresa novo = invocation.getArgument(0);
                    novo.setId(30L);
                    novo.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    return novo;
                });
        when(lojaRepository.findFirstByEmpresaAndAtivoTrueOrderByIdAsc(empresa))
                .thenReturn(Optional.of(loja));

        ConviteEmpresaResponse response = service.criarConvite(request);

        assertEquals("equipe@teste.com", response.getEmail());
        assertEquals(StatusConvite.PENDENTE, response.getStatus());
        assertEquals(StatusConvite.EXPIRADO, convite.getStatus());
        verify(conviteRepository).saveAll(List.of(convite));
    }

    private AceitarConviteRequest request(String token) {
        AceitarConviteRequest request = new AceitarConviteRequest();
        request.setToken(token);
        return request;
    }
}
