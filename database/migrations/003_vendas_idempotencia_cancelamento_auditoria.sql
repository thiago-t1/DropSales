-- BLOCO B: idempotencia, cancelamento logico, auditoria e timestamps UTC.
--
-- Execute em janela de manutencao, depois da 002, e publique o backend
-- compativel na mesma janela. O backend anterior ainda exclui vendas ao cancelar.
-- O valor historico de created_at era gravado sem fuso pelo Render (UTC);
-- por isso o backfill interpreta explicitamente esses timestamps como UTC.

BEGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE vendas
    ADD COLUMN IF NOT EXISTS idempotency_key UUID,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS motivo_cancelamento VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cancelada_por_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelada_em TIMESTAMPTZ;

UPDATE vendas
SET idempotency_key = COALESCE(
        idempotency_key,
        md5('dropsales-venda-legada-' || id::text)::uuid
    ),
    status = COALESCE(status, 'CONCLUIDA');

UPDATE vendas
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

DO $$
DECLARE
    tipo_created_at TEXT;
BEGIN
    SELECT data_type
      INTO tipo_created_at
      FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND table_name = 'vendas'
       AND column_name = 'created_at';

    IF tipo_created_at = 'timestamp without time zone' THEN
        ALTER TABLE vendas
            ALTER COLUMN created_at TYPE TIMESTAMPTZ
            USING created_at AT TIME ZONE 'UTC';
    END IF;
END
$$;

ALTER TABLE vendas
    ALTER COLUMN idempotency_key SET DEFAULT uuid_generate_v4(),
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'CONCLUIDA',
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN created_at SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'uq_vendas_usuario_idempotency'
           AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas
            ADD CONSTRAINT uq_vendas_usuario_idempotency
            UNIQUE (usuario_id, idempotency_key);
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'fk_vendas_cancelada_por'
           AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas
            ADD CONSTRAINT fk_vendas_cancelada_por
            FOREIGN KEY (cancelada_por_id) REFERENCES usuarios(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'chk_vendas_status'
           AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas ADD CONSTRAINT chk_vendas_status
            CHECK (status IN ('CONCLUIDA', 'CANCELADA')) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'chk_vendas_cancelamento'
           AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas ADD CONSTRAINT chk_vendas_cancelamento CHECK (
            (status = 'CONCLUIDA'
                AND motivo_cancelamento IS NULL
                AND cancelada_por_id IS NULL
                AND cancelada_em IS NULL)
            OR
            (status = 'CANCELADA'
                AND motivo_cancelamento IS NOT NULL
                AND LENGTH(TRIM(motivo_cancelamento)) > 0
                AND cancelada_por_id IS NOT NULL
                AND cancelada_em IS NOT NULL)
        ) NOT VALID;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_vendas_usuario_status
    ON vendas(usuario_id, status);

CREATE TABLE IF NOT EXISTS vendas_auditoria (
    id                  BIGSERIAL       PRIMARY KEY,
    venda_id            BIGINT          NOT NULL REFERENCES vendas(id),
    tipo                VARCHAR(20)     NOT NULL,
    responsavel_id      BIGINT          NOT NULL REFERENCES usuarios(id),
    descricao           VARCHAR(1000)   NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_vendas_auditoria_tipo
        CHECK (tipo IN ('CRIADA', 'EDITADA', 'CANCELADA'))
);

CREATE INDEX IF NOT EXISTS idx_vendas_auditoria_venda
    ON vendas_auditoria(venda_id);

INSERT INTO vendas_auditoria (
    venda_id,
    tipo,
    responsavel_id,
    descricao,
    created_at
)
SELECT
    venda.id,
    'CRIADA',
    venda.usuario_id,
    'Registro historico anterior a implantacao da auditoria',
    venda.created_at
FROM vendas venda
WHERE NOT EXISTS (
    SELECT 1
    FROM vendas_auditoria auditoria
    WHERE auditoria.venda_id = venda.id
      AND auditoria.tipo = 'CRIADA'
);

ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_status;
ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_cancelamento;

COMMIT;
