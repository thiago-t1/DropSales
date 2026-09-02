package com.dropsales.controller;

import com.dropsales.dto.AlterarSenhaRequest;
import com.dropsales.dto.UsuarioResponse;
import com.dropsales.dto.UsuarioUpdateRequest;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.Usuario;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.JwtTokenProvider;
import com.dropsales.util.EmailNormalizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private static final long TAMANHO_MAXIMO_FOTO = 5L * 1024 * 1024;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> getMe(Authentication authentication) {
        Usuario u = getUsuario(authentication);
        return ResponseEntity.ok(toResponse(u));
    }

    @PutMapping("/me")
    public ResponseEntity<UsuarioResponse> updateMe(
            @Valid @RequestBody UsuarioUpdateRequest request,
            Authentication authentication) {
        Usuario u = getUsuario(authentication);
        String email = EmailNormalizer.normalize(request.getEmail());
        if (!u.getEmail().equalsIgnoreCase(email)
                && usuarioRepository.existsByEmailIgnoreCaseAndIdNot(email, u.getId())) {
            throw new BusinessException("Email ja cadastrado");
        }
        u.setNome(request.getNome());
        u.setEmail(email);
        usuarioRepository.save(u);
        return ResponseEntity.ok(toResponse(u, jwtTokenProvider.generateToken(u.getId())));
    }

    @PostMapping("/me/foto")
    public ResponseEntity<UsuarioResponse> uploadFoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (file.getSize() > TAMANHO_MAXIMO_FOTO) {
            throw new BusinessException("A foto deve ter no maximo 5 MB");
        }
        byte[] conteudo = file.getBytes();
        String contentType = detectarTipoImagem(conteudo);
        if (contentType == null) {
            throw new BusinessException("A foto deve ser uma imagem JPEG, PNG ou WebP valida");
        }
        Usuario u = getUsuario(authentication);
        u.setFotoPerfil(conteudo);
        u.setFotoContentType(contentType);
        usuarioRepository.save(u);
        return ResponseEntity.ok(toResponse(u));
    }

    @GetMapping("/me/foto")
    public ResponseEntity<byte[]> getFoto(Authentication authentication) {
        Usuario u = getUsuario(authentication);
        if (u.getFotoPerfil() == null || u.getFotoPerfil().length == 0) {
            return ResponseEntity.notFound().build();
        }
        String contentType = detectarTipoImagem(u.getFotoPerfil());
        if (contentType == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header("X-Content-Type-Options", "nosniff")
                .body(u.getFotoPerfil());
    }

    @PutMapping("/me/senha")
    public ResponseEntity<Void> alterarSenha(
            @Valid @RequestBody AlterarSenhaRequest request,
            Authentication authentication) {
        if (!request.getNovaSenha().equals(request.getConfirmarSenha())) {
            return ResponseEntity.badRequest().build();
        }
        Usuario u = getUsuario(authentication);
        if (!passwordEncoder.matches(request.getSenhaAtual(), u.getSenha())) {
            throw new BusinessException("Senha atual incorreta");
        }
        u.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuarioRepository.save(u);
        return ResponseEntity.noContent().build();
    }

    private Usuario getUsuario(Authentication auth) {
        return usuarioRepository.findByEmailIgnoreCase(
                        EmailNormalizer.normalize(auth.getName()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado"));
    }

    private String detectarTipoImagem(byte[] bytes) {
        if (comecaCom(bytes, 0xFF, 0xD8, 0xFF)) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (comecaCom(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (comecaCom(bytes, 0x52, 0x49, 0x46, 0x46)
                && comecaComNaPosicao(bytes, 8, 0x57, 0x45, 0x42, 0x50)) {
            return "image/webp";
        }
        return null;
    }

    private boolean comecaCom(byte[] bytes, int... assinatura) {
        return comecaComNaPosicao(bytes, 0, assinatura);
    }

    private boolean comecaComNaPosicao(byte[] bytes, int inicio, int... assinatura) {
        if (bytes == null || bytes.length < inicio + assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if ((bytes[inicio + i] & 0xFF) != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    private UsuarioResponse toResponse(Usuario u) {
        return toResponse(u, null);
    }

    private UsuarioResponse toResponse(Usuario u, String token) {
        return UsuarioResponse.builder()
                .id(u.getId())
                .nome(u.getNome())
                .email(u.getEmail())
                .perfil(u.getPerfil().name())
                .temFoto(u.getFotoPerfil() != null && u.getFotoPerfil().length > 0)
                .token(token)
                .build();
    }
}
