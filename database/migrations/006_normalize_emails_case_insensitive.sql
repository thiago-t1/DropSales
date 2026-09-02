-- Normaliza identidades por email e garante unicidade sem diferenciar maiusculas.
-- Execute depois da migration 005.

BEGIN;

LOCK TABLE usuarios IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT LOWER(BTRIM(email))
        FROM usuarios
        GROUP BY LOWER(BTRIM(email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existem usuarios com emails duplicados ao ignorar maiusculas e espacos. Resolva-os antes de aplicar a migration 006.';
    END IF;
END
$$;

UPDATE usuarios
SET email = LOWER(BTRIM(email))
WHERE email IS DISTINCT FROM LOWER(BTRIM(email));

UPDATE convites_empresa
SET email = LOWER(BTRIM(email))
WHERE email IS DISTINCT FROM LOWER(BTRIM(email));

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuarios_email_normalizado
    ON usuarios ((LOWER(BTRIM(email))));

COMMIT;
