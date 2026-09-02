import assert from 'node:assert/strict';
import test from 'node:test';
import { papelPermitido } from '../utils/role-permission.utils.ts';

const administracao = ['PROPRIETARIO', 'ADMINISTRADOR'] as const;

test('limita areas administrativas a proprietario e administrador', () => {
  assert.equal(papelPermitido('PROPRIETARIO', administracao), true);
  assert.equal(papelPermitido('ADMINISTRADOR', administracao), true);
  assert.equal(papelPermitido('GERENTE', administracao), false);
  assert.equal(papelPermitido('OPERADOR', administracao), false);
  assert.equal(papelPermitido(null, administracao), false);
});
