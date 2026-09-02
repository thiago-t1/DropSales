package com.dropsales.service;

import com.dropsales.dto.LoginRequest;
import com.dropsales.dto.LoginResponse;
import com.dropsales.dto.RegisterRequest;
import com.dropsales.model.Perfil;
import com.dropsales.model.Usuario;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.JwtTokenProvider;
import com.dropsales.security.AuthenticationDefenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TenantProvisioningService provisioningService;
    @Mock private AuthenticationDefenseService authenticationDefenseService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                usuarioRepository,
                passwordEncoder,
                authenticationManager,
                jwtTokenProvider,
                provisioningService,
                authenticationDefenseService);
    }

    @Test
    void loginNormalizaEmailAntesDeAutenticarEConsultar() {
        LoginRequest request = new LoginRequest();
        request.setEmail(" Admin@Teste.COM ");
        request.setSenha("segredo");
        Usuario usuario = Usuario.builder()
                .id(9L)
                .nome("Admin")
                .email("admin@teste.com")
                .perfil(Perfil.OPERADOR)
                .ativo(true)
                .build();

        when(usuarioRepository.findByEmailIgnoreCase("admin@teste.com"))
                .thenReturn(Optional.of(usuario));
        when(jwtTokenProvider.generateToken(9L)).thenReturn("jwt");

        LoginResponse response = service.login(request, "127.0.0.1");

        assertEquals("admin@teste.com", response.getEmail());
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("admin@teste.com", "segredo"));
        verify(usuarioRepository).findByEmailIgnoreCase("admin@teste.com");
        verify(authenticationDefenseService).recordSuccess("admin@teste.com");
    }

    @Test
    void cadastroPersisteEmailNormalizadoEConsultaDuplicidadeSemDiferenciarCaixa() {
        RegisterRequest request = new RegisterRequest();
        request.setNome("Nova pessoa");
        request.setNomeEmpresa("Empresa");
        request.setEmail(" Pessoa@Teste.COM ");
        request.setSenha("segredo");
        when(passwordEncoder.encode("segredo")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(10L);
            return usuario;
        });

        service.register(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).existsByEmailIgnoreCase("pessoa@teste.com");
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertEquals("pessoa@teste.com", usuarioCaptor.getValue().getEmail());
        assertEquals(Perfil.ADMIN, usuarioCaptor.getValue().getPerfil());
        verify(provisioningService).criarEstruturaInicial(
                usuarioCaptor.getValue(),
                "Empresa");
    }
}
