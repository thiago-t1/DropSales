package com.dropsales.service;

import com.dropsales.dto.*;
import com.dropsales.exception.BusinessException;
import com.dropsales.model.*;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.JwtTokenProvider;
import com.dropsales.security.AuthenticationDefenseService;
import com.dropsales.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TenantProvisioningService tenantProvisioningService;
    private final AuthenticationDefenseService authenticationDefenseService;

    public LoginResponse login(LoginRequest request, String remoteAddress) {
        String email = EmailNormalizer.normalize(request.getEmail());
        authenticationDefenseService.assertAllowed(email, remoteAddress);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getSenha())
            );
        } catch (AuthenticationException ex) {
            authenticationDefenseService.recordFailure(email, remoteAddress);
            throw ex;
        }

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas"));
        authenticationDefenseService.recordSuccess(email);
        String token = jwtTokenProvider.generateToken(usuario.getId());

        return LoginResponse.builder()
                .token(token)
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .perfil(usuario.getPerfil().name())
                .build();
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email ja cadastrado");
        }

        Usuario usuario = Usuario.builder()
                .nome(request.getNome())
                .email(email)
                .senha(passwordEncoder.encode(request.getSenha()))
                .perfil(Perfil.ADMIN)
                .ativo(true)
                .build();

        usuario = usuarioRepository.save(usuario);
        tenantProvisioningService.criarEstruturaInicial(usuario, request.getNomeEmpresa());
    }
}
