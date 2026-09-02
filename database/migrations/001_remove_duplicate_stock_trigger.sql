-- A aplicacao ja atualiza o estoque dentro da transacao de VendaService.
-- Este gatilho antigo descontava o estoque uma segunda vez no PostgreSQL.
-- A migracao e idempotente e pode ser executada no Neon existente.

BEGIN;

DROP TRIGGER IF EXISTS trg_abater_estoque ON itens_venda;
DROP FUNCTION IF EXISTS fn_abater_estoque();

COMMIT;
