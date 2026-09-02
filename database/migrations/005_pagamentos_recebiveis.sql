-- Taxas configuraveis, adquirentes, pagamentos divididos, troco e recebiveis.
-- Execute depois da migration 004.

BEGIN;

-- O cabecalho de venda passa a ser apenas um resumo agregado dos pagamentos.
-- MISTO explicita vendas divididas; taxas fixas impedem recomputar o valor da
-- taxa apenas pelo percentual efetivo arredondado.
ALTER TABLE vendas DROP CONSTRAINT IF EXISTS chk_vendas_forma_pagamento;
ALTER TABLE vendas DROP CONSTRAINT IF EXISTS chk_vendas_taxa_percentual;
ALTER TABLE vendas DROP CONSTRAINT IF EXISTS chk_vendas_taxa_por_forma;
ALTER TABLE vendas DROP CONSTRAINT IF EXISTS chk_vendas_valor_liquido;
ALTER TABLE vendas DROP CONSTRAINT IF EXISTS chk_vendas_resumo_pagamento;
ALTER TABLE vendas ADD CONSTRAINT chk_vendas_forma_pagamento CHECK (
    forma_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO_DEBITO', 'CARTAO_CREDITO', 'MISTO')
) NOT VALID;
ALTER TABLE vendas ADD CONSTRAINT chk_vendas_taxa_percentual CHECK (
    taxa_pagamento_percentual BETWEEN 0 AND 100
) NOT VALID;
ALTER TABLE vendas ADD CONSTRAINT chk_vendas_resumo_pagamento CHECK (
    total >= 0
    AND taxa_pagamento_valor >= 0
    AND taxa_pagamento_valor <= total
    AND valor_liquido = total - taxa_pagamento_valor
) NOT VALID;

CREATE TABLE IF NOT EXISTS adquirentes (
    id          BIGSERIAL PRIMARY KEY,
    loja_id     BIGINT NOT NULL REFERENCES lojas(id),
    nome        VARCHAR(100) NOT NULL,
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_adquirente_loja_nome UNIQUE (loja_id, nome),
    CONSTRAINT uq_adquirente_id_loja UNIQUE (id, loja_id)
);

CREATE TABLE IF NOT EXISTS configuracoes_taxa_pagamento (
    id                      BIGSERIAL PRIMARY KEY,
    loja_id                 BIGINT NOT NULL REFERENCES lojas(id),
    forma_pagamento         VARCHAR(24) NOT NULL,
    adquirente_id           BIGINT REFERENCES adquirentes(id),
    bandeira                VARCHAR(40),
    parcelas                INTEGER NOT NULL DEFAULT 1,
    taxa_percentual         DECIMAL(7,4) NOT NULL DEFAULT 0,
    taxa_fixa               DECIMAL(12,2) NOT NULL DEFAULT 0,
    prazo_recebimento_dias  INTEGER NOT NULL DEFAULT 0,
    ativo                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_adquirente_loja FOREIGN KEY (adquirente_id, loja_id)
        REFERENCES adquirentes(id, loja_id),
    CONSTRAINT chk_config_taxa_forma CHECK (
        forma_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO_DEBITO', 'CARTAO_CREDITO')
    ),
    CONSTRAINT chk_config_taxa_parcelas CHECK (parcelas BETWEEN 1 AND 18),
    CONSTRAINT chk_config_taxa_valores CHECK (
        taxa_percentual BETWEEN 0 AND 100
        AND taxa_fixa >= 0
        AND prazo_recebimento_dias BETWEEN 0 AND 365
    )
);

CREATE TABLE IF NOT EXISTS pagamentos_venda (
    id                      BIGSERIAL PRIMARY KEY,
    venda_id                BIGINT NOT NULL REFERENCES vendas(id),
    loja_id                 BIGINT NOT NULL REFERENCES lojas(id),
    forma_pagamento         VARCHAR(24) NOT NULL,
    adquirente_id           BIGINT REFERENCES adquirentes(id),
    bandeira                VARCHAR(40),
    parcelas                INTEGER NOT NULL DEFAULT 1,
    valor_bruto             DECIMAL(12,2) NOT NULL,
    taxa_percentual         DECIMAL(7,4) NOT NULL DEFAULT 0,
    taxa_fixa               DECIMAL(12,2) NOT NULL DEFAULT 0,
    taxa_valor              DECIMAL(12,2) NOT NULL DEFAULT 0,
    valor_liquido           DECIMAL(12,2) NOT NULL,
    valor_recebido          DECIMAL(12,2),
    troco                   DECIMAL(12,2),
    prazo_recebimento_dias  INTEGER NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    substituido_em          TIMESTAMPTZ,
    cancelado_em            TIMESTAMPTZ,
    CONSTRAINT uq_pagamento_id_venda_loja UNIQUE (id, venda_id, loja_id),
    CONSTRAINT fk_pagamento_venda_loja FOREIGN KEY (venda_id, loja_id)
        REFERENCES vendas(id, loja_id),
    CONSTRAINT fk_pagamento_adquirente_loja FOREIGN KEY (adquirente_id, loja_id)
        REFERENCES adquirentes(id, loja_id),
    CONSTRAINT chk_pagamento_forma CHECK (
        forma_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO_DEBITO', 'CARTAO_CREDITO')
    ),
    CONSTRAINT chk_pagamento_status CHECK (
        status IN ('ATIVO', 'SUBSTITUIDO', 'CANCELADO')
    ),
    CONSTRAINT chk_pagamento_valores CHECK (
        valor_bruto > 0 AND taxa_valor >= 0 AND valor_liquido >= 0
        AND valor_liquido = valor_bruto - taxa_valor
    ),
    CONSTRAINT chk_pagamento_troco CHECK (
        (forma_pagamento = 'DINHEIRO'
            AND valor_recebido IS NOT NULL
            AND troco IS NOT NULL
            AND valor_recebido >= valor_bruto
            AND troco = valor_recebido - valor_bruto)
        OR
        (forma_pagamento <> 'DINHEIRO' AND valor_recebido IS NULL AND troco IS NULL)
    )
);

ALTER TABLE pagamentos_venda
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ATIVO',
    ADD COLUMN IF NOT EXISTS substituido_em TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelado_em TIMESTAMPTZ;
UPDATE pagamentos_venda SET status = 'ATIVO' WHERE status IS NULL;
ALTER TABLE pagamentos_venda ALTER COLUMN status SET NOT NULL;

CREATE TABLE IF NOT EXISTS recebiveis (
    id                  BIGSERIAL PRIMARY KEY,
    loja_id             BIGINT NOT NULL REFERENCES lojas(id),
    venda_id            BIGINT NOT NULL REFERENCES vendas(id),
    pagamento_venda_id  BIGINT NOT NULL REFERENCES pagamentos_venda(id),
    numero_parcela      INTEGER NOT NULL,
    total_parcelas      INTEGER NOT NULL,
    valor_bruto         DECIMAL(12,2) NOT NULL,
    taxa_valor          DECIMAL(12,2) NOT NULL,
    valor_liquido       DECIMAL(12,2) NOT NULL,
    data_prevista       DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    recebido_em         TIMESTAMPTZ,
    recebido_por_id     BIGINT REFERENCES usuarios(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recebivel_pagamento_parcela UNIQUE (pagamento_venda_id, numero_parcela),
    CONSTRAINT fk_recebivel_pagamento_venda_loja
        FOREIGN KEY (pagamento_venda_id, venda_id, loja_id)
        REFERENCES pagamentos_venda(id, venda_id, loja_id),
    CONSTRAINT chk_recebivel_status CHECK (status IN ('PENDENTE', 'RECEBIDO', 'CANCELADO')),
    CONSTRAINT chk_recebivel_valores CHECK (
        numero_parcela BETWEEN 1 AND total_parcelas
        AND valor_bruto > 0 AND taxa_valor >= 0 AND valor_liquido >= 0
        AND valor_liquido = valor_bruto - taxa_valor
    )
);

-- Reforca no banco que as colunas de loja redundantes nao podem divergir.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_adquirente_id_loja') THEN
        ALTER TABLE adquirentes ADD CONSTRAINT uq_adquirente_id_loja UNIQUE (id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_config_adquirente_loja') THEN
        ALTER TABLE configuracoes_taxa_pagamento
            ADD CONSTRAINT fk_config_adquirente_loja
            FOREIGN KEY (adquirente_id, loja_id) REFERENCES adquirentes(id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_pagamento_id_venda_loja') THEN
        ALTER TABLE pagamentos_venda
            ADD CONSTRAINT uq_pagamento_id_venda_loja UNIQUE (id, venda_id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pagamento_venda_loja') THEN
        ALTER TABLE pagamentos_venda
            ADD CONSTRAINT fk_pagamento_venda_loja
            FOREIGN KEY (venda_id, loja_id) REFERENCES vendas(id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pagamento_adquirente_loja') THEN
        ALTER TABLE pagamentos_venda
            ADD CONSTRAINT fk_pagamento_adquirente_loja
            FOREIGN KEY (adquirente_id, loja_id) REFERENCES adquirentes(id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_recebivel_pagamento_venda_loja') THEN
        ALTER TABLE recebiveis
            ADD CONSTRAINT fk_recebivel_pagamento_venda_loja
            FOREIGN KEY (pagamento_venda_id, venda_id, loja_id)
            REFERENCES pagamentos_venda(id, venda_id, loja_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_taxa_pagamento_loja_forma
    ON configuracoes_taxa_pagamento(loja_id, forma_pagamento);
CREATE INDEX IF NOT EXISTS idx_pagamento_venda ON pagamentos_venda(venda_id);
CREATE INDEX IF NOT EXISTS idx_pagamento_loja ON pagamentos_venda(loja_id);
CREATE INDEX IF NOT EXISTS idx_pagamento_venda_status
    ON pagamentos_venda(venda_id, status);
CREATE INDEX IF NOT EXISTS idx_recebivel_loja_status_data
    ON recebiveis(loja_id, status, data_prevista);

-- Evita regras duplicadas inclusive quando adquirente/bandeira sao NULL.
DELETE FROM configuracoes_taxa_pagamento atual
USING configuracoes_taxa_pagamento mantida
WHERE atual.id > mantida.id
  AND atual.loja_id = mantida.loja_id
  AND atual.forma_pagamento = mantida.forma_pagamento
  AND atual.adquirente_id IS NOT DISTINCT FROM mantida.adquirente_id
  AND UPPER(atual.bandeira) IS NOT DISTINCT FROM UPPER(mantida.bandeira)
  AND atual.parcelas = mantida.parcelas;
CREATE UNIQUE INDEX IF NOT EXISTS uq_config_taxa_regra
    ON configuracoes_taxa_pagamento (
        loja_id,
        forma_pagamento,
        COALESCE(adquirente_id, 0),
        COALESCE(UPPER(bandeira), ''),
        parcelas
    );

-- Converte o pagamento unico legado em pagamento/recebivel, sem duplicar ao
-- reaplicar a migration. Pix/dinheiro ficam recebidos; cartoes ficam pendentes.
INSERT INTO pagamentos_venda (
    venda_id, loja_id, forma_pagamento, parcelas, valor_bruto,
    taxa_percentual, taxa_fixa, taxa_valor, valor_liquido,
    valor_recebido, troco, prazo_recebimento_dias, status
)
SELECT
    v.id, v.loja_id, v.forma_pagamento, 1, v.total,
    v.taxa_pagamento_percentual, 0, v.taxa_pagamento_valor, v.valor_liquido,
    CASE WHEN v.forma_pagamento = 'DINHEIRO' THEN v.total ELSE NULL END,
    CASE WHEN v.forma_pagamento = 'DINHEIRO' THEN 0 ELSE NULL END,
    CASE
        WHEN v.forma_pagamento IN ('DINHEIRO', 'PIX') THEN 0
        WHEN v.forma_pagamento = 'CARTAO_DEBITO' THEN 1
        ELSE 30
    END,
    CASE WHEN v.status = 'CANCELADA' THEN 'CANCELADO' ELSE 'ATIVO' END
FROM vendas v
WHERE v.total > 0
  AND NOT EXISTS (SELECT 1 FROM pagamentos_venda p WHERE p.venda_id = v.id);

INSERT INTO recebiveis (
    loja_id, venda_id, pagamento_venda_id, numero_parcela, total_parcelas,
    valor_bruto, taxa_valor, valor_liquido, data_prevista, status, recebido_em
)
SELECT
    p.loja_id, p.venda_id, p.id, 1, 1,
    p.valor_bruto, p.taxa_valor, p.valor_liquido,
    (v.created_at AT TIME ZONE COALESCE(l.timezone, 'America/Sao_Paulo'))::date
        + p.prazo_recebimento_dias,
    CASE
        WHEN v.status = 'CANCELADA' THEN 'CANCELADO'
        WHEN p.prazo_recebimento_dias = 0
             AND p.forma_pagamento IN ('DINHEIRO', 'PIX') THEN 'RECEBIDO'
        ELSE 'PENDENTE'
    END,
    CASE
        WHEN v.status = 'CONCLUIDA'
             AND p.prazo_recebimento_dias = 0
             AND p.forma_pagamento IN ('DINHEIRO', 'PIX') THEN v.created_at
        ELSE NULL
    END
FROM pagamentos_venda p
JOIN vendas v ON v.id = p.venda_id
JOIN lojas l ON l.id = p.loja_id
WHERE NOT EXISTS (SELECT 1 FROM recebiveis r WHERE r.pagamento_venda_id = p.id);

UPDATE pagamentos_venda p
SET status = 'CANCELADO',
    cancelado_em = COALESCE(cancelado_em, v.cancelada_em, v.created_at)
FROM vendas v
WHERE p.venda_id = v.id
  AND v.status = 'CANCELADA'
  AND p.status <> 'CANCELADO';

UPDATE recebiveis r
SET status = 'CANCELADO'
FROM vendas v
WHERE r.venda_id = v.id
  AND v.status = 'CANCELADA'
  AND r.status <> 'CANCELADO';

ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_forma_pagamento;
ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_taxa_percentual;
ALTER TABLE vendas VALIDATE CONSTRAINT chk_vendas_resumo_pagamento;

COMMIT;
