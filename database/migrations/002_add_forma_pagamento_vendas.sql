-- Adiciona o snapshot financeiro da forma de pagamento em vendas existentes.
-- A migracao e idempotente: registros antigos passam a ser PIX, sem taxa,
-- e o valor liquido inicial corresponde ao total bruto da venda.
-- Execute em uma janela curta sem novas vendas e publique o backend compatível
-- na mesma janela. O backend anterior nao preenche os novos snapshots.

BEGIN;

ALTER TABLE vendas
    ADD COLUMN IF NOT EXISTS forma_pagamento VARCHAR(20),
    ADD COLUMN IF NOT EXISTS taxa_pagamento_percentual DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS taxa_pagamento_valor DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS valor_liquido DECIMAL(12,2);

UPDATE vendas
SET forma_pagamento = COALESCE(forma_pagamento, 'PIX'),
    taxa_pagamento_percentual = COALESCE(taxa_pagamento_percentual, 0);

UPDATE vendas
SET taxa_pagamento_valor = COALESCE(
        taxa_pagamento_valor,
        ROUND(total * taxa_pagamento_percentual / 100, 2)
    );

UPDATE vendas
SET valor_liquido = COALESCE(
        valor_liquido,
        ROUND(total - taxa_pagamento_valor, 2)
    );

ALTER TABLE vendas
    ALTER COLUMN forma_pagamento SET DEFAULT 'PIX',
    ALTER COLUMN forma_pagamento SET NOT NULL,
    ALTER COLUMN taxa_pagamento_percentual SET DEFAULT 0,
    ALTER COLUMN taxa_pagamento_percentual SET NOT NULL,
    ALTER COLUMN taxa_pagamento_valor SET DEFAULT 0,
    ALTER COLUMN taxa_pagamento_valor SET NOT NULL,
    ALTER COLUMN valor_liquido SET DEFAULT 0,
    ALTER COLUMN valor_liquido SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_vendas_forma_pagamento'
          AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas ADD CONSTRAINT chk_vendas_forma_pagamento CHECK (
            forma_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO_DEBITO', 'CARTAO_CREDITO')
        ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_vendas_taxa_percentual'
          AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas ADD CONSTRAINT chk_vendas_taxa_percentual CHECK (
            taxa_pagamento_percentual >= 0 AND taxa_pagamento_percentual <= 5
        ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_vendas_taxa_por_forma'
          AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas ADD CONSTRAINT chk_vendas_taxa_por_forma CHECK (
            (forma_pagamento IN ('DINHEIRO', 'PIX')
                AND taxa_pagamento_percentual = 0
                AND taxa_pagamento_valor = 0)
            OR
            (forma_pagamento IN ('CARTAO_DEBITO', 'CARTAO_CREDITO')
                AND taxa_pagamento_percentual > 0
                AND taxa_pagamento_percentual <= 5
                AND taxa_pagamento_valor >= 0)
        ) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_vendas_valor_liquido'
          AND conrelid = 'vendas'::regclass
    ) THEN
        ALTER TABLE vendas ADD CONSTRAINT chk_vendas_valor_liquido CHECK (
            valor_liquido >= 0
            AND valor_liquido <= total
            AND taxa_pagamento_valor = ROUND(total * taxa_pagamento_percentual / 100, 2)
            AND valor_liquido = total - taxa_pagamento_valor
        ) NOT VALID;
    END IF;
END
$$;

ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_forma_pagamento;
ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_taxa_percentual;
ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_taxa_por_forma;
ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_valor_liquido;

COMMIT;
