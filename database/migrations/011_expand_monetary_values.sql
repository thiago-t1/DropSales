-- Permite vendas de alto valor sem estouro do NUMERIC(12,2).
-- Mantem duas casas decimais e amplia apenas a parte inteira.
ALTER TABLE vendas
    ALTER COLUMN total TYPE NUMERIC(19,2),
    ALTER COLUMN taxa_pagamento_valor TYPE NUMERIC(19,2),
    ALTER COLUMN valor_liquido TYPE NUMERIC(19,2);

ALTER TABLE itens_venda
    ALTER COLUMN preco_unitario TYPE NUMERIC(19,2),
    ALTER COLUMN subtotal TYPE NUMERIC(19,2);

ALTER TABLE transacoes
    ALTER COLUMN valor TYPE NUMERIC(19,2);

ALTER TABLE configuracoes_taxa_pagamento
    ALTER COLUMN taxa_fixa TYPE NUMERIC(19,2);

ALTER TABLE pagamentos_venda
    ALTER COLUMN valor_bruto TYPE NUMERIC(19,2),
    ALTER COLUMN taxa_fixa TYPE NUMERIC(19,2),
    ALTER COLUMN taxa_valor TYPE NUMERIC(19,2),
    ALTER COLUMN valor_liquido TYPE NUMERIC(19,2),
    ALTER COLUMN valor_recebido TYPE NUMERIC(19,2),
    ALTER COLUMN troco TYPE NUMERIC(19,2);

ALTER TABLE recebiveis
    ALTER COLUMN valor_bruto TYPE NUMERIC(19,2),
    ALTER COLUMN taxa_valor TYPE NUMERIC(19,2),
    ALTER COLUMN valor_liquido TYPE NUMERIC(19,2);
