import test from 'node:test';
import assert from 'node:assert/strict';
import {
  formatarInteiroBrasileiro,
  formatarMoedaBrasileira,
  lerInteiroFormatado,
  lerMoedaBrasileira,
  nomeProdutoValido,
  proximoCodigoProduto,
  sanitizarNomeProduto,
} from './product-input.utils.ts';

test('sanitiza nome sem remover caracteres comerciais usuais', () => {
  assert.equal(sanitizarNomeProduto('Camiseta @@@ Premium!!'), 'Camiseta Premium');
  assert.equal(nomeProdutoValido("Café d'Ávila - 500g"), true);
  assert.equal(nomeProdutoValido('Produto @@@'), false);
});

test('gera código sequencial apenas a partir da lista informada', () => {
  assert.equal(proximoCodigoProduto(['ITEM-0002', 'LEGADO-8', 'item-0010']), 'ITEM-0011');
  assert.equal(proximoCodigoProduto([]), 'ITEM-0001');
});

test('converte moeda e estoque no padrão brasileiro', () => {
  assert.equal(lerMoedaBrasileira('300.000,50'), 300000.5);
  assert.equal(formatarMoedaBrasileira(300000.5), '300.000,50');
  assert.equal(lerInteiroFormatado('10.000'), 10000);
  assert.equal(formatarInteiroBrasileiro(10000), '10.000');
});
