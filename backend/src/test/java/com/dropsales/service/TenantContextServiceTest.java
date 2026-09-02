package com.dropsales.service;

import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ForbiddenException;
import com.dropsales.model.Empresa;
import com.dropsales.model.Loja;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.Usuario;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.MembroEmpresaRepository;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContextServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MembroEmpresaRepository membroRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private TenantProvisioningService provisioningService;
    @Mock private HttpServletRequest request;

    private TenantContextService service;
    private Usuario usuario;
    private Empresa empresa;
    private MembroEmpresa membro;
    private Loja lojaA;
    private Loja lojaB;

    @BeforeEach
    void setUp() {
        service = new TenantContextService(
                usuarioRepository,
                membroRepository,
                lojaRepository,
                provisioningService,
                request);
        usuario = Usuario.builder()
                .id(1L)
                .nome("Usuario")
                .email("usuario@teste.com")
                .build();
        empresa = Empresa.builder()
                .id(2L)
                .nome("Empresa")
                .ativo(true)
                .build();
        membro = MembroEmpresa.builder()
                .id(3L)
                .empresa(empresa)
                .usuario(usuario)
                .papel(PapelEmpresa.PROPRIETARIO)
                .ativo(true)
                .build();
        lojaA = loja(10L, "A");
        lojaB = loja(20L, "B");
    }

    @Test
    void mutacaoComMaisDeUmaLojaExigeHeaderExplicito() {
        prepararUsuario();
        when(request.getHeader(TenantContextService.LOJA_HEADER)).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(lojaRepository.findByEmpresaAndAtivoTrueOrderByNomeAsc(empresa))
                .thenReturn(List.of(lojaA, lojaB));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserEmail)
                    .thenReturn(usuario.getEmail());

            assertThrows(BusinessException.class, service::atual);
        }
    }

    @Test
    void headerSelecionaLojaAcessivelSemMisturarContexto() {
        prepararUsuario();
        when(request.getHeader(TenantContextService.LOJA_HEADER))
                .thenReturn(lojaB.getId().toString());
        when(lojaRepository.findById(lojaB.getId()))
                .thenReturn(Optional.of(lojaB));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserEmail)
                    .thenReturn(usuario.getEmail());

            TenantContextService.ContextoAtual contexto = service.atual();

            assertEquals(lojaB, contexto.loja());
            assertEquals(empresa, contexto.empresa());
            assertEquals(usuario, contexto.usuario());
        }
    }

    @Test
    void empresaInativaNaoPodeSerUsadaMesmoComMembroAtivo() {
        empresa.setAtivo(false);
        prepararUsuario();

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserEmail)
                    .thenReturn(usuario.getEmail());

            assertThrows(BusinessException.class, service::atual);
        }
    }

    @Test
    void operadorPodeConsultarMasNaoPassaPelaPermissaoDeGerencia() {
        membro.setPapel(PapelEmpresa.OPERADOR);
        prepararUsuario();
        when(request.getHeader(TenantContextService.LOJA_HEADER)).thenReturn(null);
        when(lojaRepository.findByEmpresaAndAtivoTrueOrderByNomeAsc(empresa))
                .thenReturn(List.of(lojaA));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserEmail)
                    .thenReturn(usuario.getEmail());

            assertThrows(ForbiddenException.class, service::exigirGerencia);
        }
    }

    @Test
    void usuarioSuspensoNaoRecebeNovaEmpresaAutomaticamente() {
        when(usuarioRepository.findByEmailIgnoreCase(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));
        when(membroRepository.findByUsuarioAndAtivoTrueOrderByIdAsc(usuario))
                .thenReturn(List.of());
        when(membroRepository.existsByUsuario(usuario)).thenReturn(true);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserEmail)
                    .thenReturn(usuario.getEmail());

            assertThrows(ForbiddenException.class, service::atual);
        }

        verify(provisioningService, never()).criarEstruturaInicial(usuario, null);
    }

    private void prepararUsuario() {
        when(usuarioRepository.findByEmailIgnoreCase(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));
        when(membroRepository.findByUsuarioAndAtivoTrueOrderByIdAsc(usuario))
                .thenReturn(List.of(membro));
    }

    private Loja loja(Long id, String nome) {
        return Loja.builder()
                .id(id)
                .empresa(empresa)
                .nome(nome)
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build();
    }
}
