-- Evita carregar o binario da foto em toda autenticacao/contexto e substitui
-- o Large Object (OID) por BYTEA em uma tabela dedicada.
CREATE TABLE IF NOT EXISTS usuario_fotos (
    usuario_id     BIGINT PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    conteudo       BYTEA NOT NULL,
    content_type   VARCHAR(50) NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
DECLARE
    tipo_foto TEXT;
BEGIN
    SELECT udt_name
      INTO tipo_foto
      FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND table_name = 'usuarios'
       AND column_name = 'foto_perfil';

    IF tipo_foto = 'oid' THEN
        INSERT INTO usuario_fotos (usuario_id, conteudo, content_type)
        SELECT u.id,
               lo_get(u.foto_perfil),
               COALESCE(NULLIF(u.foto_content_type, ''), 'application/octet-stream')
          FROM usuarios u
         WHERE u.foto_perfil IS NOT NULL
           AND EXISTS (
               SELECT 1
                 FROM pg_largeobject_metadata metadata
                WHERE metadata.oid = u.foto_perfil
           )
        ON CONFLICT (usuario_id) DO NOTHING;
    END IF;
END $$;
