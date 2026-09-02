package com.dropsales.security;

import com.dropsales.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Filtro que intercepta cada request e valida o token JWT no header Authorization.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final int MAX_BEARER_TOKEN_LENGTH = 4096;
    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token)
                && SecurityContextHolder.getContext().getAuthentication() == null
                && tokenProvider.validateToken(token)) {
            try {
                String subject = tokenProvider.getSubjectFromToken(token);
                UserDetails userDetails = loadUserDetails(subject);

                if (contaDisponivel(userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | UsernameNotFoundException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean contaDisponivel(UserDetails userDetails) {
        return userDetails.isEnabled()
                && userDetails.isAccountNonLocked()
                && userDetails.isAccountNonExpired()
                && userDetails.isCredentialsNonExpired();
    }

    private UserDetails loadUserDetails(String subject) {
        if (subject != null && subject.chars().allMatch(Character::isDigit)) {
            return userDetailsService.loadUserById(Long.parseLong(subject));
        }
        return userDetailsService.loadUserByUsername(subject);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer)
                && bearer.startsWith("Bearer ")
                && bearer.length() <= MAX_BEARER_TOKEN_LENGTH) {
            String token = bearer.substring(7).trim();
            return token.isEmpty() || token.contains(" ") ? null : token;
        }
        return null;
    }
}
