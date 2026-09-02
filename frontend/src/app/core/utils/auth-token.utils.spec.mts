import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isJwtUsable,
  shouldEndSessionForUnauthorized,
} from './auth-token.utils.ts';

function tokenComPayload(payload: object): string {
  const encoded = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `header.${encoded}.signature`;
}

test('aceita somente JWT bem formado e ainda nao expirado', () => {
  const now = 1_700_000_000_000;

  assert.equal(isJwtUsable(tokenComPayload({ exp: 1_700_000_100 }), now), true);
  assert.equal(isJwtUsable(tokenComPayload({ exp: 1_699_999_999 }), now), false);
  assert.equal(isJwtUsable(tokenComPayload({}), now), false);
  assert.equal(isJwtUsable('token-invalido', now), false);
});

test('401 protegido encerra apenas a sessao que originou a requisicao', () => {
  assert.equal(
    shouldEndSessionForUnauthorized(401, '/api/produtos', 'token-a', 'token-a'),
    true,
  );
  assert.equal(
    shouldEndSessionForUnauthorized(401, '/api/produtos', 'token-a', 'token-novo'),
    false,
  );
  assert.equal(
    shouldEndSessionForUnauthorized(401, '/api/auth/login', 'token-a', 'token-a'),
    false,
  );
  assert.equal(
    shouldEndSessionForUnauthorized(400, '/api/produtos', 'token-a', 'token-a'),
    false,
  );
});
