-- =============================================================
-- DropSales — Script de Inicialização do Banco de Dados
-- PostgreSQL 15+
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -----------------------------------------------
-- TABELA: usuarios
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS usuarios (
    id              BIGSERIAL       PRIMARY KEY,
    nome            VARCHAR(150)    NOT NULL,
    email           VARCHAR(200)    NOT NULL UNIQUE,
    senha           VARCHAR(255)    NOT NULL,  -- Hash BCrypt
    perfil          VARCHAR(20)     NOT NULL DEFAULT 'OPERADOR'
                        CHECK (perfil IN ('ADMIN', 'OPERADOR')),
    ativo           BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------
-- TABELA: categorias
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS categorias (
    id              BIGSERIAL       PRIMARY KEY,
    nome            VARCHAR(100)    NOT NULL UNIQUE,
    descricao       TEXT,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------
-- TABELA: produtos
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS produtos (
    id                  BIGSERIAL       PRIMARY KEY,
    nome                VARCHAR(200)    NOT NULL,
    descricao           TEXT,
    sku                 VARCHAR(50)     UNIQUE,
    preco_custo         DECIMAL(12,2)   NOT NULL DEFAULT 0,
    preco_venda         DECIMAL(12,2)   NOT NULL,
    quantidade_estoque  INTEGER         NOT NULL DEFAULT 0,
    estoque_minimo      INTEGER         NOT NULL DEFAULT 5,
    categoria_id        BIGINT          REFERENCES categorias(id),
    ativo               BOOLEAN         DEFAULT TRUE,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_preco_venda_positivo CHECK (preco_venda >= 0),
    CONSTRAINT chk_preco_custo_positivo CHECK (preco_custo >= 0),
    CONSTRAINT chk_estoque_positivo     CHECK (quantidade_estoque >= 0)
);

-- -----------------------------------------------
-- TABELA: vendas (cabecalho)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS vendas (
    id              BIGSERIAL       PRIMARY KEY,
    usuario_id      BIGINT          NOT NULL REFERENCES usuarios(id),
    total           DECIMAL(12,2)   NOT NULL DEFAULT 0,
    observacao      TEXT,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------
-- TABELA: itens_venda (detalhe)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS itens_venda (
    id              BIGSERIAL       PRIMARY KEY,
    venda_id        BIGINT          NOT NULL REFERENCES vendas(id) ON DELETE CASCADE,
    produto_id      BIGINT          NOT NULL REFERENCES produtos(id),
    quantidade      INTEGER         NOT NULL,
    preco_unitario  DECIMAL(12,2)   NOT NULL,
    subtotal        DECIMAL(12,2)   NOT NULL,
    CONSTRAINT chk_quantidade_positiva CHECK (quantidade > 0)
);

-- -----------------------------------------------
-- TABELA: transacoes (receitas, despesas, contas)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS transacoes (
    id                  BIGSERIAL       PRIMARY KEY,
    descricao           VARCHAR(300)    NOT NULL,
    valor               DECIMAL(12,2)   NOT NULL,
    tipo                VARCHAR(20)     NOT NULL
                            CHECK (tipo IN ('RECEITA', 'DESPESA')),
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDENTE'
                            CHECK (status IN ('PENDENTE', 'PAGO', 'CANCELADO', 'VENCIDO')),
    data_vencimento     DATE,
    data_pagamento      DATE,
    venda_id            BIGINT          REFERENCES vendas(id),
    usuario_id          BIGINT          NOT NULL REFERENCES usuarios(id),
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------
-- INDICES
-- -----------------------------------------------
CREATE INDEX IF NOT EXISTS idx_usuarios_email        ON usuarios(email);
CREATE INDEX IF NOT EXISTS idx_produtos_categoria    ON produtos(categoria_id);
CREATE INDEX IF NOT EXISTS idx_produtos_sku          ON produtos(sku);
CREATE INDEX IF NOT EXISTS idx_vendas_usuario        ON vendas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_itens_venda_venda     ON itens_venda(venda_id);
CREATE INDEX IF NOT EXISTS idx_itens_venda_produto   ON itens_venda(produto_id);
CREATE INDEX IF NOT EXISTS idx_transacoes_tipo       ON transacoes(tipo);
CREATE INDEX IF NOT EXISTS idx_transacoes_status     ON transacoes(status);
CREATE INDEX IF NOT EXISTS idx_transacoes_vencimento ON transacoes(data_vencimento);

-- -----------------------------------------------
-- TRIGGER: Abater estoque ao inserir item de venda
-- -----------------------------------------------
CREATE OR REPLACE FUNCTION fn_abater_estoque()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE produtos
    SET quantidade_estoque = quantidade_estoque - NEW.quantidade,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.produto_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Produto ID % nao encontrado', NEW.produto_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_abater_estoque
    AFTER INSERT ON itens_venda
    FOR EACH ROW
    EXECUTE FUNCTION fn_abater_estoque();

-- -----------------------------------------------
-- SEED: Dados iniciais
-- -----------------------------------------------
INSERT INTO usuarios (nome, email, senha, perfil) VALUES
('Administrador', 'admin@dropsales.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN');

INSERT INTO categorias (nome, descricao) VALUES
('Eletronicos',  'Produtos eletronicos em geral'),
('Vestuario',    'Roupas e acessorios'),
('Alimentos',    'Produtos alimenticios'),
('Outros',       'Produtos diversos');
