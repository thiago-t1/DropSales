-- Defesa em profundidade para limites ja validados na aplicacao.
-- Execute depois da migration 008. A migration falha se houver dados legados
-- invalidos, exigindo reconciliacao antes de ativar a restricao.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'chk_pagamento_venda_parcelas'
           AND conrelid = 'pagamentos_venda'::regclass
    ) THEN
        ALTER TABLE pagamentos_venda
            ADD CONSTRAINT chk_pagamento_venda_parcelas
            CHECK (parcelas BETWEEN 1 AND 18);
    END IF;
END
$$;

COMMIT;
