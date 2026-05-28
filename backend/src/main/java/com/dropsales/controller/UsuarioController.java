package com.dropsales.controller;

import com.dropsales.dto.AlterarSenhaRequest;
import com.dropsales.dto.UsuarioResponse;
import com.dropsales.dto.UsuarioUpdateRequest;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.Usuario;
import com.dropsales.repository.UsuarioRepository;
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

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

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
        u.setNome(request.getNome());
        u.setEmail(request.getEmail());
        usuarioRepository.save(u);
        return ResponseEntity.ok(toResponse(u));
    }

    @PostMapping("/me/foto")
    public ResponseEntity<UsuarioResponse> uploadFoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Usuario u = getUsuario(authentication);
        u.setFotoPerfil(file.getBytes());
        u.setFotoContentType(file.getContentType());
        usuarioRepository.save(u);
        return ResponseEntity.ok(toResponse(u));
    }

    @GetMapping("/me/foto")
    public ResponseEntity<byte[]> getFoto(Authentication authentication) {
        Usuario u = getUsuario(authentication);
        if (u.getFotoPerfil() == null || u.getFotoPerfil().length == 0) {
            return ResponseEntity.notFound().build();
        }
        String contentType = u.getFotoContentType() != null ? u.getFotoContentType() : "image/jpeg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
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
            return ResponseEntity.status(401).build();
        }
        u.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuarioRepository.save(u);
        return ResponseEntity.noContent().build();
    }

    private Usuario getUsuario(Authentication auth) {
        return usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado"));
    }

    private UsuarioResponse toResponse(Usuario u) {
        return UsuarioResponse.builder()
                .id(u.getId())
                .nome(u.getNome())
                .email(u.getEmail())
                .perfil(u.getPerfil().name())
                .temFoto(u.getFotoPerfil() != null && u.getFotoPerfil().length > 0)
                .build();
    }
}