-- =============================================================
-- DropSales - schema consolidado para PostgreSQL 15+
-- Inclui multiempresa/multiloja, vendas auditaveis e recebiveis.
-- Para bancos existentes, aplique migrations/001..008 em ordem.
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS usuarios (
    id                  BIGSERIAL PRIMARY KEY,
    nome                VARCHAR(150) NOT NULL,
    email               VARCHAR(200) NOT NULL UNIQUE,
    senha               VARCHAR(255) NOT NULL,
    perfil              VARCHAR(20) NOT NULL DEFAULT 'OPERADOR'
                            CHECK (perfil IN ('ADMIN', 'OPERADOR')),
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    foto_perfil         OID,
    foto_content_type   VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS empresas (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(160) NOT NULL,
    documento   VARCHAR(20),
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lojas (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  BIGINT NOT NULL REFERENCES empresas(id),
    nome        VARCHAR(120) NOT NULL,
    timezone    VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lojas_empresa_nome UNIQUE (empresa_id, nome)
);

CREATE TABLE IF NOT EXISTS membros_empresa (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  BIGINT NOT NULL REFERENCES empresas(id),
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id),
    papel       VARCHAR(24) NOT NULL
                    CHECK (papel IN (
                        'PROPRIETARIO', 'ADMINISTRADOR', 'GERENTE', 'OPERADOR'
                    )),
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_membro_empresa_usuario UNIQUE (empresa_id, usuario_id)
);

CREATE TABLE IF NOT EXISTS convites_empresa (
    id                  BIGSERIAL PRIMARY KEY,
    empresa_id          BIGINT NOT NULL REFERENCES empresas(id),
    email               VARCHAR(200) NOT NULL,
    papel               VARCHAR(24) NOT NULL
                            CHECK (papel IN (
                                'ADMINISTRADOR', 'GERENTE', 'OPERADOR'
                            )),
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                            CHECK (status IN (
                                'PENDENTE', 'ACEITO', 'REVOGADO', 'EXPIRADO'
                            )),
    expires_at          TIMESTAMPTZ NOT NULL,
    convidado_por_id    BIGINT NOT NULL REFERENCES usuarios(id),
    aceito_por_id       BIGINT REFERENCES usuarios(id),
    accepted_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categorias (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    descricao   TEXT,
    loja_id     BIGINT NOT NULL REFERENCES lojas(id),
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_categorias_loja_nome UNIQUE (loja_id, nome),
    CONSTRAINT uq_categorias_id_loja UNIQUE (id, loja_id)
);

CREATE TABLE IF NOT EXISTS produtos (
    id                      BIGSERIAL PRIMARY KEY,
    nome                    VARCHAR(200) NOT NULL,
    descricao               TEXT,
    sku                     VARCHAR(50),
    preco_custo             DECIMAL(12,2) NOT NULL DEFAULT 0
                                CHECK (preco_custo >= 0),
    preco_venda             DECIMAL(12,2) NOT NULL
                                CHECK (preco_venda >= 0),
    quantidade_estoque      INTEGER NOT NULL DEFAULT 0
                                CHECK (quantidade_estoque >= 0),
    estoque_minimo          INTEGER NOT NULL DEFAULT 5,
    categoria_id            BIGINT,
    loja_id                 BIGINT NOT NULL REFERENCES lojas(id),
    usuario_id              BIGINT NOT NULL REFERENCES usuarios(id),
    ativo                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_produtos_loja_sku UNIQUE (loja_id, sku),
    CONSTRAINT fk_produto_categoria_loja
        FOREIGN KEY (categoria_id, loja_id)
        REFERENCES categorias(id, loja_id)
);

CREATE TABLE IF NOT EXISTS vendas (
    id                          BIGSERIAL PRIMARY KEY,
    loja_id                     BIGINT NOT NULL REFERENCES lojas(id),
    usuario_id                  BIGINT NOT NULL REFERENCES usuarios(id),
    idempotency_key             UUID NOT NULL DEFAULT uuid_generate_v4(),
    idempotency_request_hash    VARCHAR(64) NOT NULL
                                    CHECK (
                                        idempotency_request_hash ~ '^[0-9a-f]{64}$'
                                    ),
    status                      VARCHAR(20) NOT NULL DEFAULT 'CONCLUIDA'
                                    CHECK (status IN ('CONCLUIDA', 'CANCELADA')),
    total                       DECIMAL(12,2) NOT NULL DEFAULT 0,
    forma_pagamento             VARCHAR(20) NOT NULL DEFAULT 'PIX'
                                    CHECK (forma_pagamento IN (
                                        'DINHEIRO', 'PIX', 'CARTAO_DEBITO',
                                        'CARTAO_CREDITO', 'MISTO'
                                    )),
    taxa_pagamento_percentual   DECIMAL(5,2) NOT NULL DEFAULT 0
                                    CHECK (
                                        taxa_pagamento_percentual BETWEEN 0 AND 100
                                    ),
    taxa_pagamento_valor        DECIMAL(12,2) NOT NULL DEFAULT 0,
    valor_liquido               DECIMAL(12,2) NOT NULL DEFAULT 0,
    observacao                  TEXT,
    motivo_cancelamento         VARCHAR(500),
    cancelada_por_id            BIGINT REFERENCES usuarios(id),
    cancelada_em                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vendas_loja_idempotency
        UNIQUE (loja_id, idempotency_key),
    CONSTRAINT uq_vendas_id_loja UNIQUE (id, loja_id),
    CONSTRAINT chk_vendas_resumo_pagamento CHECK (
        total >= 0
        AND taxa_pagamento_valor >= 0
        AND taxa_pagamento_valor <= total
        AND valor_liquido = total - taxa_pagamento_valor
    ),
    CONSTRAINT chk_vendas_cancelamento CHECK (
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
    )
);

CREATE TABLE IF NOT EXISTS vendas_auditoria (
    id              BIGSERIAL PRIMARY KEY,
    venda_id        BIGINT NOT NULL REFERENCES vendas(id),
    tipo            VARCHAR(20) NOT NULL
                        CHECK (tipo IN ('CRIADA', 'EDITADA', 'CANCELADA')),
    responsavel_id  BIGINT NOT NULL REFERENCES usuarios(id),
    descricao       VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS itens_venda (
    id              BIGSERIAL PRIMARY KEY,
    venda_id        BIGINT NOT NULL REFERENCES vendas(id) ON DELETE CASCADE,
    produto_id      BIGINT NOT NULL REFERENCES produtos(id),
    quantidade      INTEGER NOT NULL CHECK (quantidade > 0),
    preco_unitario  DECIMAL(12,2) NOT NULL,
    subtotal        DECIMAL(12,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS transacoes (
    id                  BIGSERIAL PRIMARY KEY,
    descricao           VARCHAR(300) NOT NULL,
    valor               DECIMAL(12,2) NOT NULL,
    tipo                VARCHAR(20) NOT NULL
                            CHECK (tipo IN ('RECEITA', 'DESPESA')),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                            CHECK (status IN (
                                'PENDENTE', 'PAGO', 'CANCELADO', 'VENCIDO'
                            )),
    data_vencimento     DATE,
    data_pagamento      DATE,
    venda_id            BIGINT REFERENCES vendas(id),
    loja_id             BIGINT NOT NULL REFERENCES lojas(id),
    usuario_id          BIGINT NOT NULL REFERENCES usuarios(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transacao_venda_loja
        FOREIGN KEY (venda_id, loja_id)
        REFERENCES vendas(id, loja_id)
);

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
    forma_pagamento         VARCHAR(24) NOT NULL
                                CHECK (forma_pagamento IN (
                                    'DINHEIRO', 'PIX', 'CARTAO_DEBITO',
                                    'CARTAO_CREDITO'
                                )),
    adquirente_id           BIGINT,
    bandeira                VARCHAR(40),
    parcelas                INTEGER NOT NULL DEFAULT 1
                                CHECK (parcelas BETWEEN 1 AND 18),
    taxa_percentual         DECIMAL(7,4) NOT NULL DEFAULT 0,
    taxa_fixa               DECIMAL(12,2) NOT NULL DEFAULT 0,
    prazo_recebimento_dias  INTEGER NOT NULL DEFAULT 0,
    ativo                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_adquirente_loja
        FOREIGN KEY (adquirente_id, loja_id)
        REFERENCES adquirentes(id, loja_id),
    CONSTRAINT chk_config_taxa_valores CHECK (
        taxa_percentual BETWEEN 0 AND 100
        AND taxa_fixa >= 0
        AND prazo_recebimento_dias BETWEEN 0 AND 365
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_config_taxa_regra
    ON configuracoes_taxa_pagamento (
        loja_id,
        forma_pagamento,
        COALESCE(adquirente_id, 0),
        COALESCE(UPPER(bandeira), ''),
        parcelas
    );

CREATE TABLE IF NOT EXISTS pagamentos_venda (
    id                      BIGSERIAL PRIMARY KEY,
    venda_id                BIGINT NOT NULL,
    loja_id                 BIGINT NOT NULL,
    forma_pagamento         VARCHAR(24) NOT NULL
                                CHECK (forma_pagamento IN (
                                    'DINHEIRO', 'PIX', 'CARTAO_DEBITO',
                                    'CARTAO_CREDITO'
                                )),
    adquirente_id           BIGINT,
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
    status                  VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
                                CHECK (status IN (
                                    'ATIVO', 'SUBSTITUIDO', 'CANCELADO'
                                )),
    substituido_em          TIMESTAMPTZ,
    cancelado_em            TIMESTAMPTZ,
    CONSTRAINT uq_pagamento_id_venda_loja UNIQUE (id, venda_id, loja_id),
    CONSTRAINT chk_pagamento_venda_parcelas CHECK (parcelas BETWEEN 1 AND 18),
    CONSTRAINT fk_pagamento_venda_loja
        FOREIGN KEY (venda_id, loja_id)
        REFERENCES vendas(id, loja_id),
    CONSTRAINT fk_pagamento_adquirente_loja
        FOREIGN KEY (adquirente_id, loja_id)
        REFERENCES adquirentes(id, loja_id),
    CONSTRAINT chk_pagamento_valores CHECK (
        valor_bruto > 0
        AND taxa_valor >= 0
        AND valor_liquido >= 0
        AND valor_liquido = valor_bruto - taxa_valor
    ),
    CONSTRAINT chk_pagamento_troco CHECK (
        (forma_pagamento = 'DINHEIRO'
            AND valor_recebido IS NOT NULL
            AND troco IS NOT NULL
            AND valor_recebido >= valor_bruto
            AND troco = valor_recebido - valor_bruto)
        OR
        (forma_pagamento <> 'DINHEIRO'
            AND valor_recebido IS NULL
            AND troco IS NULL)
    )
);

CREATE TABLE IF NOT EXISTS recebiveis (
    id                  BIGSERIAL PRIMARY KEY,
    loja_id             BIGINT NOT NULL,
    venda_id            BIGINT NOT NULL,
    pagamento_venda_id  BIGINT NOT NULL,
    numero_parcela      INTEGER NOT NULL,
    total_parcelas      INTEGER NOT NULL,
    valor_bruto         DECIMAL(12,2) NOT NULL,
    taxa_valor          DECIMAL(12,2) NOT NULL,
    valor_liquido       DECIMAL(12,2) NOT NULL,
    data_prevista       DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                            CHECK (status IN (
                                'PENDENTE', 'RECEBIDO', 'CANCELADO'
                            )),
    recebido_em         TIMESTAMPTZ,
    recebido_por_id     BIGINT REFERENCES usuarios(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recebivel_pagamento_parcela
        UNIQUE (pagamento_venda_id, numero_parcela),
    CONSTRAINT fk_recebivel_pagamento_venda_loja
        FOREIGN KEY (pagamento_venda_id, venda_id, loja_id)
        REFERENCES pagamentos_venda(id, venda_id, loja_id),
    CONSTRAINT chk_recebivel_valores CHECK (
        numero_parcela BETWEEN 1 AND total_parcelas
        AND valor_bruto > 0
        AND taxa_valor >= 0
        AND valor_liquido >= 0
        AND valor_liquido = valor_bruto - taxa_valor
    )
);

CREATE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios(email);
CREATE UNIQUE INDEX IF NOT EXISTS uq_usuarios_email_normalizado
    ON usuarios ((LOWER(BTRIM(email))));
CREATE INDEX IF NOT EXISTS idx_lojas_empresa ON lojas(empresa_id);
CREATE INDEX IF NOT EXISTS idx_membros_usuario ON membros_empresa(usuario_id, ativo);
CREATE INDEX IF NOT EXISTS idx_convite_empresa_status
    ON convites_empresa(empresa_id, status);
CREATE INDEX IF NOT EXISTS idx_categorias_loja ON categorias(loja_id);
CREATE INDEX IF NOT EXISTS idx_produtos_loja ON produtos(loja_id);
CREATE INDEX IF NOT EXISTS idx_produtos_categoria ON produtos(categoria_id);
CREATE INDEX IF NOT EXISTS idx_vendas_loja_status ON vendas(loja_id, status);
CREATE INDEX IF NOT EXISTS idx_vendas_auditoria_venda
    ON vendas_auditoria(venda_id);
CREATE INDEX IF NOT EXISTS idx_itens_venda_venda ON itens_venda(venda_id);
CREATE INDEX IF NOT EXISTS idx_itens_venda_produto ON itens_venda(produto_id);
CREATE INDEX IF NOT EXISTS idx_transacoes_loja ON transacoes(loja_id);
CREATE INDEX IF NOT EXISTS idx_transacoes_tipo_status
    ON transacoes(loja_id, tipo, status);
CREATE INDEX IF NOT EXISTS idx_taxa_pagamento_loja_forma
    ON configuracoes_taxa_pagamento(loja_id, forma_pagamento);
CREATE INDEX IF NOT EXISTS idx_pagamento_venda
    ON pagamentos_venda(venda_id);
CREATE INDEX IF NOT EXISTS idx_pagamento_venda_status
    ON pagamentos_venda(venda_id, status);
CREATE INDEX IF NOT EXISTS idx_pagamento_loja ON pagamentos_venda(loja_id);
CREATE INDEX IF NOT EXISTS idx_recebivel_loja_status_data
    ON recebiveis(loja_id, status, data_prevista);

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

-- Estoque e atualizado exclusivamente na camada de servico Java.
DROP TRIGGER IF EXISTS trg_abater_estoque ON itens_venda;
DROP FUNCTION IF EXISTS fn_abater_estoque();
