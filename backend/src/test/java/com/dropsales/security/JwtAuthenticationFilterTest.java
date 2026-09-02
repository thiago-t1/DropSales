package com.dropsales.security;

import com.dropsales.service.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider tokenProvider;
    @Mock private CustomUserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService);
        userDetails = User.withUsername("email-atual@teste.com")
                .password("senha")
                .roles("OPERADOR")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void carregaUsuarioPeloIdDosTokensNovos() throws Exception {
        autenticarComSubject("token-id", "42");
        when(userDetailsService.loadUserById(42L)).thenReturn(userDetails);

        executarFiltro("token-id");

        verify(userDetailsService).loadUserById(42L);
        assertEquals("email-atual@teste.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void aceitaTokenAntigoPorEmailDuranteTransicao() throws Exception {
        autenticarComSubject("token-legado", "email-antigo@teste.com");
        when(userDetailsService.loadUserByUsername("email-antigo@teste.com")).thenReturn(userDetails);

        executarFiltro("token-legado");

        verify(userDetailsService).loadUserByUsername("email-antigo@teste.com");
        assertEquals("email-atual@teste.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void naoAutenticaContaDesativadaMesmoComTokenValido() throws Exception {
        UserDetails usuarioDesativado = User.withUsername("inativo@teste.com")
                .password("senha")
                .roles("OPERADOR")
                .disabled(true)
                .build();
        autenticarComSubject("token-inativo", "77");
        when(userDetailsService.loadUserById(77L)).thenReturn(usuarioDesativado);

        executarFiltro("token-inativo");

        verify(userDetailsService).loadUserById(77L);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void autenticarComSubject(String token, String subject) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getSubjectFromToken(token)).thenReturn(subject);
    }

    private void executarFiltro(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/usuarios/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
    }
}
