package com.dropsales.repository;

import com.dropsales.model.UsuarioFoto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class UsuarioFotoRepositoryTest {

    @Autowired private UsuarioFotoRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void segundaFotoSubstituiAAnteriorNoMesmoUsuario() {
        repository.saveAndFlush(UsuarioFoto.builder()
                .usuarioId(42L)
                .conteudo(new byte[] {1, 2, 3})
                .contentType("image/png")
                .build());

        repository.saveAndFlush(UsuarioFoto.builder()
                .usuarioId(42L)
                .conteudo(new byte[] {9, 8, 7, 6})
                .contentType("image/webp")
                .build());
        entityManager.clear();

        UsuarioFoto atual = repository.findById(42L).orElseThrow();
        assertArrayEquals(new byte[] {9, 8, 7, 6}, atual.getConteudo());
        assertEquals("image/webp", atual.getContentType());
    }
}
