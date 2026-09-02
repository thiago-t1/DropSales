import test from 'node:test';
import assert from 'node:assert/strict';
import {
  agruparPorForma,
  calcularResumoFechamento,
  dataNoFuso,
  normalizarSecaoCaixa,
} from './caixa.utils.ts';

const base = {
  id: 1,
  vendaId: 10,
  adquirente: null,
  numeroParcela: 1,
  totalParcelas: 1,
  dataPrevista: '2026-07-28',
  status: 'RECEBIDO' as const,
};

test('converte o instante para a data da loja antes do fechamento', () => {
  assert.equal(dataNoFuso('2026-07-28T02:30:00Z', 'America/Sao_Paulo'), '2026-07-27');
  assert.equal(dataNoFuso('2026-07-28T12:00:00Z', 'America/Sao_Paulo'), '2026-07-28');
  assert.equal(dataNoFuso('2026-07-28T12:00:00Z', 'Fuso/Invalido'), '2026-07-28');
});

test('normaliza links diretos para uma seção válida do caixa', () => {
  assert.equal(normalizarSecaoCaixa('movimentacoes'), 'movimentacoes');
  assert.equal(normalizarSecaoCaixa('fechamento'), 'fechamento');
  assert.equal(normalizarSecaoCaixa('qualquer-coisa'), 'visao-geral');
  assert.equal(normalizarSecaoCaixa(null), 'visao-geral');
});

test('agrupa entradas confirmadas por forma de pagamento', () => {
  const grupos = agruparPorForma([
    {
      ...base,
      formaPagamento: 'PIX',
      valorBruto: 40,
      taxaValor: 0,
      valorLiquido: 40,
      recebidoEm: '2026-07-28T12:00:00Z',
    },
    {
      ...base,
      id: 2,
      formaPagamento: 'PIX',
      valorBruto: 10,
      taxaValor: 0,
      valorLiquido: 10,
      recebidoEm: '2026-07-28T13:00:00Z',
    },
    {
      ...base,
      id: 3,
      formaPagamento: 'CARTAO_DEBITO',
      valorBruto: 30,
      taxaValor: 0.9,
      valorLiquido: 29.1,
      recebidoEm: '2026-07-28T14:00:00Z',
    },
  ]);

  assert.deepEqual(grupos, [
    {
      formaPagamento: 'PIX',
      quantidade: 2,
      valorBruto: 50,
      taxaValor: 0,
      valorLiquido: 50,
    },
    {
      formaPagamento: 'CARTAO_DEBITO',
      quantidade: 1,
      valorBruto: 30,
      taxaValor: 0.9,
      valorLiquido: 29.1,
    },
  ]);
});

test('fecha somente as entradas confirmadas na data e no fuso selecionados', () => {
  const resumo = calcularResumoFechamento([
    {
      ...base,
      formaPagamento: 'DINHEIRO',
      valorBruto: 100,
      taxaValor: 0,
      valorLiquido: 100,
      recebidoEm: '2026-07-28T12:00:00Z',
    },
    {
      ...base,
      id: 2,
      formaPagamento: 'CARTAO_CREDITO',
      valorBruto: 50,
      taxaValor: 2.5,
      valorLiquido: 47.5,
      recebidoEm: '2026-07-28T14:00:00Z',
    },
    {
      ...base,
      id: 3,
      formaPagamento: 'PIX',
      valorBruto: 20,
      taxaValor: 0,
      valorLiquido: 20,
      recebidoEm: '2026-07-28T02:30:00Z',
    },
    {
      ...base,
      id: 4,
      formaPagamento: 'PIX',
      valorBruto: 70,
      taxaValor: 0,
      valorLiquido: 70,
      status: 'PENDENTE',
      recebidoEm: null,
    },
  ], '2026-07-28', 'America/Sao_Paulo');

  assert.equal(resumo.quantidade, 2);
  assert.equal(resumo.valorBruto, 150);
  assert.equal(resumo.taxaValor, 2.5);
  assert.equal(resumo.valorLiquido, 147.5);
  assert.equal(resumo.dinheiro, 100);
  assert.equal(resumo.pix, 0);
  assert.equal(resumo.cartoes, 47.5);
});
