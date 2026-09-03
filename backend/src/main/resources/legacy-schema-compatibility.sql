-- Compatibilidade idempotente para bancos criados pelas primeiras versões.
-- Executado antes do Hibernate somente quando SQL_INIT_MODE=always.
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS foto_perfil OID,
    ADD COLUMN IF NOT EXISTS foto_content_type VARCHAR(50);

ALTER TABLE categorias
    ADD COLUMN IF NOT EXISTS usuario_id BIGINT,
    ADD COLUMN IF NOT EXISTS loja_id BIGINT;

ALTER TABLE produtos
    ADD COLUMN IF NOT EXISTS usuario_id BIGINT,
    ADD COLUMN IF NOT EXISTS loja_id BIGINT;

ALTER TABLE vendas
    ADD COLUMN IF NOT EXISTS loja_id BIGINT,
    ADD COLUMN IF NOT EXISTS forma_pagamento VARCHAR(20) DEFAULT 'PIX',
    ADD COLUMN IF NOT EXISTS taxa_pagamento_percentual DECIMAL(5,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS taxa_pagamento_valor DECIMAL(12,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS valor_liquido DECIMAL(12,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS idempotency_key UUID,
    ADD COLUMN IF NOT EXISTS idempotency_request_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'CONCLUIDA',
    ADD COLUMN IF NOT EXISTS motivo_cancelamento VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cancelada_por_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelada_em TIMESTAMPTZ;

ALTER TABLE transacoes
    ADD COLUMN IF NOT EXISTS loja_id BIGINT;
