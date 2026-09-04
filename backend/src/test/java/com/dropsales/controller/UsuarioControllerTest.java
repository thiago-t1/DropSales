package com.dropsales.controller;

import com.dropsales.dto.AlterarSenhaRequest;
import com.dropsales.dto.UsuarioResponse;
import com.dropsales.dto.UsuarioUpdateRequest;
import com.dropsales.exception.BusinessException;
import com.dropsales.model.Perfil;
import com.dropsales.model.Usuario;
import com.dropsales.model.UsuarioFoto;
import com.dropsales.repository.UsuarioFotoRepository;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioFotoRepository usuarioFotoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private Authentication authentication;

    private UsuarioController controller;

    @BeforeEach
    void setUp() {
        controller = new UsuarioController(
                usuarioRepository,
                usuarioFotoRepository,
                passwordEncoder,
                jwtTokenProvider);
    }

    @Test
    void alterarEmailDevolveTokenNovoBaseadoNoId() {
        Usuario usuario = Usuario.builder()
                .id(42L)
                .nome("Nome antigo")
                .email("antigo@teste.com")
                .senha("hash")
                .perfil(Perfil.OPERADOR)
                .ativo(true)
                .build();
        UsuarioUpdateRequest request = new UsuarioUpdateRequest();
        request.setNome("Nome novo");
        request.setEmail(" Novo@Teste.COM ");

        when(authentication.getName()).thenReturn("antigo@teste.com");
        when(usuarioRepository.findByEmailIgnoreCase("antigo@teste.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByEmailIgnoreCaseAndIdNot("novo@teste.com", 42L)).thenReturn(false);
        when(jwtTokenProvider.generateToken(42L)).thenReturn("jwt-por-id");

        ResponseEntity<UsuarioResponse> response = controller.updateMe(request, authentication);

        assertEquals("novo@teste.com", response.getBody().getEmail());
        assertEquals("jwt-por-id", response.getBody().getToken());
        verify(usuarioRepository).save(usuario);
        verify(jwtTokenProvider).generateToken(42L);
    }

    @Test
    void senhaAtualIncorretaERegraDeNegocioEnaoFalhaDeSessao() {
        Usuario usuario = Usuario.builder()
                .id(42L)
                .nome("Usuario")
                .email("usuario@teste.com")
                .senha("hash")
                .perfil(Perfil.OPERADOR)
                .ativo(true)
                .build();
        AlterarSenhaRequest request = new AlterarSenhaRequest();
        request.setSenhaAtual("incorreta");
        request.setNovaSenha("nova-senha");
        request.setConfirmarSenha("nova-senha");

        when(authentication.getName()).thenReturn(usuario.getEmail());
        when(usuarioRepository.findByEmailIgnoreCase(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorreta", "hash")).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.alterarSenha(request, authentication));

        assertEquals("Senha atual incorreta", error.getMessage());
        verify(usuarioRepository, never()).save(usuario);
    }

    @Test
    void uploadDetectaPngPelosBytesEIgnoraContentTypeDeclarado() throws Exception {
        Usuario usuario = usuarioComFoto(null, null);
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
        };
        MockMultipartFile arquivo = new MockMultipartFile(
                "file",
                "foto.txt",
                MediaType.TEXT_PLAIN_VALUE,
                png);

        when(authentication.getName()).thenReturn(usuario.getEmail());
        when(usuarioRepository.findByEmailIgnoreCase(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        controller.uploadFoto(arquivo, authentication);

        ArgumentCaptor<UsuarioFoto> captor = ArgumentCaptor.forClass(UsuarioFoto.class);
        verify(usuarioFotoRepository).saveAndFlush(captor.capture());
        assertArrayEquals(png, captor.getValue().getConteudo());
        assertEquals(MediaType.IMAGE_PNG_VALUE, captor.getValue().getContentType());
        assertEquals(usuario.getId(), captor.getValue().getUsuarioId());
    }

    @Test
    void uploadRejeitaArquivoSemAssinaturaDeImagem() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file",
                "ataque.html",
                MediaType.IMAGE_PNG_VALUE,
                "<script>alert(1)</script>".getBytes());

        assertThrows(
                BusinessException.class,
                () -> controller.uploadFoto(arquivo, authentication));

        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uploadRejeitaFotoAcimaDeCincoMegabytes() {
        byte[] conteudo = new byte[(5 * 1024 * 1024) + 1];
        conteudo[0] = (byte) 0xFF;
        conteudo[1] = (byte) 0xD8;
        conteudo[2] = (byte) 0xFF;
        MockMultipartFile arquivo = new MockMultipartFile(
                "file",
                "foto.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                conteudo);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> controller.uploadFoto(arquivo, authentication));

        assertEquals("A foto deve ter no maximo 5 MB", error.getMessage());
        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void downloadRedetectaTipoConfiavelMesmoParaRegistroLegado() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        Usuario usuario = usuarioComFoto(jpeg, MediaType.TEXT_HTML_VALUE);
        when(authentication.getName()).thenReturn(usuario.getEmail());
        when(usuarioRepository.findByEmailIgnoreCase(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));
        when(usuarioFotoRepository.findById(usuario.getId()))
                .thenReturn(Optional.of(UsuarioFoto.builder()
                        .usuarioId(usuario.getId())
                        .conteudo(jpeg)
                        .contentType(MediaType.TEXT_HTML_VALUE)
                        .build()));

        ResponseEntity<byte[]> response = controller.getFoto(authentication);

        assertEquals(MediaType.IMAGE_JPEG_VALUE, response.getHeaders().getFirst("Content-Type"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertArrayEquals(jpeg, response.getBody());
    }

    private Usuario usuarioComFoto(byte[] bytes, String contentType) {
        return Usuario.builder()
                .id(42L)
                .nome("Usuario")
                .email("usuario@teste.com")
                .senha("hash")
                .perfil(Perfil.OPERADOR)
                .ativo(true)
                .build();
    }
}
