-- Empresa/Loja, equipe e isolamento de dados por loja.
-- Execute depois da migration 003. A conversao cria uma empresa e uma loja
-- principal para cada usuario legado e preserva todos os registros existentes.

BEGIN;

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
    papel       VARCHAR(24) NOT NULL,
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_membro_empresa_usuario UNIQUE (empresa_id, usuario_id),
    CONSTRAINT chk_membro_empresa_papel CHECK (
        papel IN ('PROPRIETARIO', 'ADMINISTRADOR', 'GERENTE', 'OPERADOR')
    )
);

CREATE TABLE IF NOT EXISTS convites_empresa (
    id                  BIGSERIAL PRIMARY KEY,
    empresa_id          BIGINT NOT NULL REFERENCES empresas(id),
    email               VARCHAR(200) NOT NULL,
    papel               VARCHAR(24) NOT NULL,
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    expires_at          TIMESTAMPTZ NOT NULL,
    convidado_por_id    BIGINT NOT NULL REFERENCES usuarios(id),
    aceito_por_id       BIGINT REFERENCES usuarios(id),
    accepted_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_convite_papel CHECK (
        papel IN ('ADMINISTRADOR', 'GERENTE', 'OPERADOR')
    ),
    CONSTRAINT chk_convite_status CHECK (
        status IN ('PENDENTE', 'ACEITO', 'REVOGADO', 'EXPIRADO')
    )
);

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS foto_perfil OID,
    ADD COLUMN IF NOT EXISTS foto_content_type VARCHAR(50);
ALTER TABLE categorias ADD COLUMN IF NOT EXISTS usuario_id BIGINT;
ALTER TABLE produtos ADD COLUMN IF NOT EXISTS usuario_id BIGINT;
ALTER TABLE categorias ADD COLUMN IF NOT EXISTS loja_id BIGINT;
ALTER TABLE produtos ADD COLUMN IF NOT EXISTS loja_id BIGINT;
ALTER TABLE vendas ADD COLUMN IF NOT EXISTS loja_id BIGINT;
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS loja_id BIGINT;

-- O init legado nao declarava usuario_id em categorias/produtos, embora as
-- entidades Java ja o utilizassem. Recupera o dono somente quando existe
-- evidencia no historico de vendas. Registros orfaos exigem reconciliacao
-- explicita antes de continuar para impedir atribuicao ao tenant errado.
UPDATE produtos produto
SET usuario_id = origem.usuario_id
FROM (
    SELECT item.produto_id, MIN(venda.usuario_id) AS usuario_id
    FROM itens_venda item
    JOIN vendas venda ON venda.id = item.venda_id
    GROUP BY item.produto_id
) origem
WHERE produto.id = origem.produto_id
  AND produto.usuario_id IS NULL;

UPDATE categorias categoria
SET usuario_id = origem.usuario_id
FROM (
    SELECT produto.categoria_id, MIN(produto.usuario_id) AS usuario_id
    FROM produtos produto
    WHERE produto.categoria_id IS NOT NULL
      AND produto.usuario_id IS NOT NULL
    GROUP BY produto.categoria_id
) origem
WHERE categoria.id = origem.categoria_id
  AND categoria.usuario_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM produtos WHERE usuario_id IS NULL)
       OR EXISTS (SELECT 1 FROM categorias WHERE usuario_id IS NULL) THEN
        RAISE EXCEPTION
            'Existem produtos/categorias sem proprietario verificavel. Reconcilie usuario_id explicitamente antes da migration 004.';
    END IF;
END
$$;

ALTER TABLE categorias ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE produtos ALTER COLUMN usuario_id SET NOT NULL;

-- Provisionamento e backfill por usuario. O bloco e reiniciavel: se o usuario
-- ja possui empresa, reutiliza a primeira empresa/loja ativa.
DO $$
DECLARE
    usuario_row RECORD;
    empresa_atual BIGINT;
    loja_atual BIGINT;
BEGIN
    FOR usuario_row IN SELECT id, nome FROM usuarios ORDER BY id LOOP
        SELECT me.empresa_id
          INTO empresa_atual
          FROM membros_empresa me
         WHERE me.usuario_id = usuario_row.id
           AND me.ativo = TRUE
         ORDER BY me.id
         LIMIT 1;

        IF empresa_atual IS NULL THEN
            INSERT INTO empresas (nome)
            VALUES ('Loja de ' || split_part(usuario_row.nome, ' ', 1))
            RETURNING id INTO empresa_atual;

            INSERT INTO membros_empresa (empresa_id, usuario_id, papel)
            VALUES (empresa_atual, usuario_row.id, 'PROPRIETARIO');
        END IF;

        SELECT l.id
          INTO loja_atual
          FROM lojas l
         WHERE l.empresa_id = empresa_atual
           AND l.ativo = TRUE
         ORDER BY l.id
         LIMIT 1;

        IF loja_atual IS NULL THEN
            INSERT INTO lojas (empresa_id, nome)
            VALUES (empresa_atual, 'Loja principal')
            RETURNING id INTO loja_atual;
        END IF;

        UPDATE categorias SET loja_id = loja_atual
         WHERE usuario_id = usuario_row.id AND loja_id IS NULL;
        UPDATE produtos SET loja_id = loja_atual
         WHERE usuario_id = usuario_row.id AND loja_id IS NULL;
        UPDATE vendas SET loja_id = loja_atual
         WHERE usuario_id = usuario_row.id AND loja_id IS NULL;
        UPDATE transacoes SET loja_id = loja_atual
         WHERE usuario_id = usuario_row.id AND loja_id IS NULL;

        empresa_atual := NULL;
        loja_atual := NULL;
    END LOOP;
END
$$;

ALTER TABLE categorias ALTER COLUMN loja_id SET NOT NULL;
ALTER TABLE produtos ALTER COLUMN loja_id SET NOT NULL;
ALTER TABLE vendas ALTER COLUMN loja_id SET NOT NULL;
ALTER TABLE transacoes ALTER COLUMN loja_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_categorias_loja') THEN
        ALTER TABLE categorias ADD CONSTRAINT fk_categorias_loja FOREIGN KEY (loja_id) REFERENCES lojas(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_categorias_usuario') THEN
        ALTER TABLE categorias ADD CONSTRAINT fk_categorias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_produtos_loja') THEN
        ALTER TABLE produtos ADD CONSTRAINT fk_produtos_loja FOREIGN KEY (loja_id) REFERENCES lojas(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_produtos_usuario') THEN
        ALTER TABLE produtos ADD CONSTRAINT fk_produtos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_vendas_loja') THEN
        ALTER TABLE vendas ADD CONSTRAINT fk_vendas_loja FOREIGN KEY (loja_id) REFERENCES lojas(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transacoes_loja') THEN
        ALTER TABLE transacoes ADD CONSTRAINT fk_transacoes_loja FOREIGN KEY (loja_id) REFERENCES lojas(id);
    END IF;
END
$$;

ALTER TABLE categorias DROP CONSTRAINT IF EXISTS uq_categorias_usuario_nome;
ALTER TABLE produtos DROP CONSTRAINT IF EXISTS uq_produtos_usuario_sku;
ALTER TABLE vendas DROP CONSTRAINT IF EXISTS uq_vendas_usuario_idempotency;

DO $$
DECLARE
    restricao RECORD;
BEGIN
    FOR restricao IN
        SELECT conrelid::regclass AS tabela, conname
        FROM pg_constraint
        WHERE contype = 'u'
          AND (
              (conrelid = 'categorias'::regclass
                  AND pg_get_constraintdef(oid) = 'UNIQUE (nome)')
              OR
              (conrelid = 'produtos'::regclass
                  AND pg_get_constraintdef(oid) = 'UNIQUE (sku)')
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE %s DROP CONSTRAINT %I',
            restricao.tabela,
            restricao.conname);
    END LOOP;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_categorias_loja_nome') THEN
        ALTER TABLE categorias ADD CONSTRAINT uq_categorias_loja_nome UNIQUE (loja_id, nome);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_produtos_loja_sku') THEN
        ALTER TABLE produtos ADD CONSTRAINT uq_produtos_loja_sku UNIQUE (loja_id, sku);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_vendas_loja_idempotency') THEN
        ALTER TABLE vendas ADD CONSTRAINT uq_vendas_loja_idempotency UNIQUE (loja_id, idempotency_key);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_categorias_id_loja') THEN
        ALTER TABLE categorias ADD CONSTRAINT uq_categorias_id_loja UNIQUE (id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_vendas_id_loja') THEN
        ALTER TABLE vendas ADD CONSTRAINT uq_vendas_id_loja UNIQUE (id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_produto_categoria_loja') THEN
        ALTER TABLE produtos ADD CONSTRAINT fk_produto_categoria_loja
            FOREIGN KEY (categoria_id, loja_id) REFERENCES categorias(id, loja_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transacao_venda_loja') THEN
        ALTER TABLE transacoes ADD CONSTRAINT fk_transacao_venda_loja
            FOREIGN KEY (venda_id, loja_id) REFERENCES vendas(id, loja_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_lojas_empresa ON lojas(empresa_id);
CREATE INDEX IF NOT EXISTS idx_membros_usuario ON membros_empresa(usuario_id, ativo);
CREATE INDEX IF NOT EXISTS idx_convite_empresa_status ON convites_empresa(empresa_id, status);
CREATE INDEX IF NOT EXISTS idx_categorias_loja ON categorias(loja_id);
CREATE INDEX IF NOT EXISTS idx_produtos_loja ON produtos(loja_id);
CREATE INDEX IF NOT EXISTS idx_vendas_loja_status ON vendas(loja_id, status);
CREATE INDEX IF NOT EXISTS idx_transacoes_loja ON transacoes(loja_id);

COMMIT;
