package com.dropsales.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    @Test
    void retornaConflitoSemExporDetalhesDoBanco() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("constraint interna sensivel"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertFalse(response.getBody().get("message").toString().contains("constraint interna sensivel"));
    }

    @Test
    void retornaNaoAutorizadoSemExporDetalhesDaAutenticacao() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response = handler.handleAuthentication(
                new BadCredentialsException("detalhe sensivel"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Email ou senha invalidos.", response.getBody().get("message"));
    }

    @Test
    void retornaConflitoQuandoChaveIdempotenteTemOutroPayload() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response = handler.handleConflict(
                new ConflictException("Chave ja utilizada com outro payload"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertEquals(
                "Chave ja utilizada com outro payload",
                response.getBody().get("message"));
    }
}
