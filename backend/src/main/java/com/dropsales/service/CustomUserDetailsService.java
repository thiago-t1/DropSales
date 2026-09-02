package com.dropsales.service;

import com.dropsales.model.Usuario;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Collections;

/**
 * Carrega usuario do banco para autenticacao Spring Security.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String emailNormalizado = EmailNormalizer.normalize(email);
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(emailNormalizado)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + email));

        return toUserDetails(usuario);
    }

    public UserDetails loadUserById(Long id) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + id));

        return toUserDetails(usuario);
    }

    private UserDetails toUserDetails(Usuario usuario) {
        return new User(
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getAtivo(),
                true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()))
        );
    }
}
