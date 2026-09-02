import assert from 'node:assert/strict';
import test from 'node:test';
import { produtoParaFormulario, produtoRequestValido } from './product-form.mapper.ts';

test('restaura descricao e categoria ao abrir um produto para edicao', () => {
  const form = produtoParaFormulario({
    id: 42,
    nome: 'Camiseta basica',
    descricao: 'Algodao, tamanho M',
    sku: 'CAM-M-001',
    precoCusto: 25,
    precoVenda: 59.9,
    quantidadeEstoque: 18,
    estoqueMinimo: 4,
    categoriaId: 7,
    categoria: 'Vestuario',
    estoqueBaixo: false,
  });

  assert.equal(form.descricao, 'Algodao, tamanho M');
  assert.equal(form.categoriaId, 7);
});

test('normaliza campos opcionais sem inventar categoria', () => {
  const form = produtoParaFormulario({
    id: 43,
    nome: 'Produto sem categoria',
    descricao: null,
    sku: '',
    precoCusto: 0,
    precoVenda: 10,
    quantidadeEstoque: 0,
    estoqueMinimo: 5,
    categoriaId: null,
    categoria: null,
    estoqueBaixo: true,
  });

  assert.equal(form.descricao, '');
  assert.equal(form.categoriaId, undefined);
});

test('valida todos os campos numericos antes do envio', () => {
  const valido = {
    nome: 'Camiseta',
    descricao: '',
    sku: 'CAM-001',
    precoCusto: 25,
    precoVenda: 59.9,
    quantidadeEstoque: 18,
    estoqueMinimo: 4,
  };

  assert.equal(produtoRequestValido(valido), true);
  assert.equal(produtoRequestValido({ ...valido, precoCusto: -1 }), false);
  assert.equal(produtoRequestValido({ ...valido, precoVenda: 10.123 }), false);
  assert.equal(produtoRequestValido({ ...valido, quantidadeEstoque: -1 }), false);
  assert.equal(produtoRequestValido({ ...valido, estoqueMinimo: 1.5 }), false);
});

test('rejeita textos e valores acima dos limites do backend', () => {
  const valido = {
    nome: 'Camiseta',
    descricao: '',
    sku: 'CAM-001',
    precoCusto: 25,
    precoVenda: 59.9,
    quantidadeEstoque: 18,
    estoqueMinimo: 4,
  };

  assert.equal(produtoRequestValido({ ...valido, nome: ' ' }), false);
  assert.equal(produtoRequestValido({ ...valido, nome: 'N'.repeat(201) }), false);
  assert.equal(produtoRequestValido({ ...valido, precoVenda: 10_000_000_000 }), false);
});
