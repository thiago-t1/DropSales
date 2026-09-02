import assert from 'node:assert/strict';
import test from 'node:test';
import { isApiRequest } from './api-request.utils.ts';

test('reconhece somente URLs pertencentes a API configurada', () => {
  assert.equal(isApiRequest('/api/produtos', '/api'), true);
  assert.equal(isApiRequest('/api', '/api/'), true);
  assert.equal(isApiRequest('/api-maliciosa/produtos', '/api'), false);
  assert.equal(
    isApiRequest('https://dropsales.onrender.com/api/vendas', 'https://dropsales.onrender.com/api'),
    true,
  );
  assert.equal(
    isApiRequest('https://terceiro.example/api/vendas', 'https://dropsales.onrender.com/api'),
    false,
  );
});
