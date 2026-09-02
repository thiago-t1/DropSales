-- Vincula cada chave de idempotencia ao payload original da venda.
-- Execute depois da migration 007.

BEGIN;

ALTER TABLE vendas
    ADD COLUMN IF NOT EXISTS idempotency_request_hash VARCHAR(64);

-- Vendas anteriores nao possuem o payload original para recomputar o hash.
-- O marcador hexadecimal impede que uma chave legada seja reutilizada
-- silenciosamente com um novo corpo de requisicao.
UPDATE vendas
SET idempotency_request_hash = REPEAT('0', 64)
WHERE idempotency_request_hash IS NULL;

ALTER TABLE vendas
    ALTER COLUMN idempotency_request_hash SET NOT NULL;

ALTER TABLE vendas
    DROP CONSTRAINT IF EXISTS chk_vendas_idempotency_request_hash;
ALTER TABLE vendas
    ADD CONSTRAINT chk_vendas_idempotency_request_hash CHECK (
        idempotency_request_hash ~ '^[0-9a-f]{64}$'
    );

COMMIT;
