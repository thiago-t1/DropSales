-- Reforcos de integridade concorrente e isolamento por loja.
-- Execute depois da migration 006.

BEGIN;

UPDATE convites_empresa
SET status = 'EXPIRADO'
WHERE status = 'PENDENTE'
  AND expires_at <= CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT empresa_id, LOWER(BTRIM(nome))
        FROM lojas
        GROUP BY empresa_id, LOWER(BTRIM(nome))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existem lojas duplicadas ao ignorar maiusculas e espacos. Resolva-as antes da migration 007.';
    END IF;
    IF EXISTS (
        SELECT loja_id, LOWER(BTRIM(nome))
        FROM categorias
        GROUP BY loja_id, LOWER(BTRIM(nome))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existem categorias duplicadas ao ignorar maiusculas e espacos. Resolva-as antes da migration 007.';
    END IF;
    IF EXISTS (
        SELECT loja_id, LOWER(BTRIM(nome))
        FROM adquirentes
        GROUP BY loja_id, LOWER(BTRIM(nome))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existem adquirentes duplicados ao ignorar maiusculas e espacos. Resolva-os antes da migration 007.';
    END IF;
    IF EXISTS (
        SELECT loja_id, LOWER(BTRIM(sku))
        FROM produtos
        WHERE sku IS NOT NULL AND BTRIM(sku) <> ''
        GROUP BY loja_id, LOWER(BTRIM(sku))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existem SKUs duplicados ao ignorar maiusculas e espacos. Resolva-os antes da migration 007.';
    END IF;
    IF EXISTS (
        SELECT empresa_id, LOWER(BTRIM(email))
        FROM convites_empresa
        WHERE status = 'PENDENTE'
        GROUP BY empresa_id, LOWER(BTRIM(email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existem convites pendentes duplicados. Resolva-os antes da migration 007.';
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lojas_empresa_nome_normalizado
    ON lojas (empresa_id, LOWER(BTRIM(nome)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_categorias_loja_nome_normalizado
    ON categorias (loja_id, LOWER(BTRIM(nome)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_adquirentes_loja_nome_normalizado
    ON adquirentes (loja_id, LOWER(BTRIM(nome)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_produtos_loja_sku_normalizado
    ON produtos (loja_id, LOWER(BTRIM(sku)))
    WHERE sku IS NOT NULL AND BTRIM(sku) <> '';
CREATE UNIQUE INDEX IF NOT EXISTS uq_convites_empresa_email_pendente
    ON convites_empresa (empresa_id, LOWER(BTRIM(email)))
    WHERE status = 'PENDENTE';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'transacoes'
          AND column_name = 'created_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE transacoes
            ALTER COLUMN created_at TYPE TIMESTAMPTZ
            USING created_at AT TIME ZONE 'UTC';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'transacoes'
          AND column_name = 'updated_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE transacoes
            ALTER COLUMN updated_at TYPE TIMESTAMPTZ
            USING updated_at AT TIME ZONE 'UTC';
    END IF;
END
$$;

ALTER TABLE pagamentos_venda
    DROP CONSTRAINT IF EXISTS chk_pagamento_valores,
    DROP CONSTRAINT IF EXISTS chk_pagamento_troco;
ALTER TABLE pagamentos_venda
    ADD CONSTRAINT chk_pagamento_valores CHECK (
        valor_bruto > 0
        AND taxa_valor >= 0
        AND valor_liquido >= 0
        AND valor_liquido = valor_bruto - taxa_valor
    ) NOT VALID,
    ADD CONSTRAINT chk_pagamento_troco CHECK (
        (forma_pagamento = 'DINHEIRO'
            AND valor_recebido IS NOT NULL
            AND troco IS NOT NULL
            AND valor_recebido >= valor_bruto
            AND troco = valor_recebido - valor_bruto)
        OR
        (forma_pagamento <> 'DINHEIRO'
            AND valor_recebido IS NULL
            AND troco IS NULL)
    ) NOT VALID;

ALTER TABLE recebiveis
    DROP CONSTRAINT IF EXISTS chk_recebivel_valores;
ALTER TABLE recebiveis
    ADD CONSTRAINT chk_recebivel_valores CHECK (
        numero_parcela BETWEEN 1 AND total_parcelas
        AND valor_bruto > 0
        AND taxa_valor >= 0
        AND valor_liquido >= 0
        AND valor_liquido = valor_bruto - taxa_valor
    ) NOT VALID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_transacao_venda_loja'
          AND conrelid = 'transacoes'::regclass
    ) THEN
        ALTER TABLE transacoes
            ADD CONSTRAINT fk_transacao_venda_loja
            FOREIGN KEY (venda_id, loja_id) REFERENCES vendas(id, loja_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_convite_empresa_status
    ON convites_empresa(empresa_id, status);
CREATE INDEX IF NOT EXISTS idx_pagamento_venda
    ON pagamentos_venda(venda_id);
DROP INDEX IF EXISTS idx_convites_empresa_status;

ALTER TABLE pagamentos_venda VALIDATE CONSTRAINT chk_pagamento_valores;
ALTER TABLE pagamentos_venda VALIDATE CONSTRAINT chk_pagamento_troco;
ALTER TABLE recebiveis VALIDATE CONSTRAINT chk_recebivel_valores;

COMMIT;
