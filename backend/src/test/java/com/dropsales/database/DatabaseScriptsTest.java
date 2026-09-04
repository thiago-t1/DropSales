package com.dropsales.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseScriptsTest {

    private static final Path DATABASE_DIR = Path.of("..", "database");

    @Test
    void schemaConsolidadoNaoCriaCredencialAdministrativa() throws IOException {
        String initSql = ler("init.sql");

        assertFalse(initSql.contains("admin@dropsales.com"));
        assertFalse(initSql.contains("$2a$10$N9qo8uLO"));
    }

    @Test
    void migrationMultitenantFalhaQuandoNaoConsegueProvarOProprietario()
            throws IOException {
        String migration = ler("migrations/004_empresa_loja_equipe_multitenancy.sql");

        assertFalse(migration.contains(
                "SET usuario_id = (SELECT id FROM usuarios ORDER BY id LIMIT 1)"));
        assertTrue(migration.contains(
                "Existem produtos/categorias sem proprietario verificavel"));
    }

    @Test
    void limiteDeParcelasExisteNoSchemaFreshENaMigration() throws IOException {
        String initSql = ler("init.sql");
        String migration = ler("migrations/009_pagamentos_constraints.sql");

        assertTrue(initSql.contains(
                "chk_pagamento_venda_parcelas CHECK (parcelas BETWEEN 1 AND 18)"));
        assertTrue(migration.contains(
                "CHECK (parcelas BETWEEN 1 AND 18)"));
    }

    @Test
    void fotoDePerfilMantemCompatibilidadeComPostgresqlOid() throws IOException {
        assertTrue(ler("init.sql").contains("foto_perfil         OID"));
        assertTrue(ler("migrations/004_empresa_loja_equipe_multitenancy.sql")
                .contains("foto_perfil OID"));
        assertTrue(ler("init.sql").contains("CREATE TABLE IF NOT EXISTS usuario_fotos"));
        assertTrue(ler("migrations/010_separate_profile_photos.sql")
                .contains("lo_get(u.foto_perfil)"));
    }

    @Test
    void bootstrapLegadoPreparaColunasAntesDoHibernate() throws IOException {
        String compatibilitySql = Files.readString(Path.of(
                "src", "main", "resources", "legacy-schema-compatibility.sql"));

        assertTrue(compatibilitySql.contains("ADD COLUMN IF NOT EXISTS loja_id BIGINT"));
        assertTrue(compatibilitySql.contains("ADD COLUMN IF NOT EXISTS forma_pagamento"));
        assertTrue(compatibilitySql.contains("ADD COLUMN IF NOT EXISTS idempotency_request_hash"));
    }

    private String ler(String relativePath) throws IOException {
        return Files.readString(DATABASE_DIR.resolve(relativePath));
    }
}
