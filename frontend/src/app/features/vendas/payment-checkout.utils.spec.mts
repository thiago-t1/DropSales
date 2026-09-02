import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateCashChange,
  calculatePaymentFee,
  paymentTotalsMatch,
  selectPaymentRule,
} from './payment-checkout.utils.ts';

const general = {
  id: 1, ativo: true, formaPagamento: 'CARTAO_CREDITO', parcelas: 2,
  adquirenteId: null, bandeira: null, taxaPercentual: 4, taxaFixa: 0,
};

test('prioriza taxa especifica de adquirente e bandeira sobre regra geral', () => {
  const specific = {
    ...general, id: 2, adquirenteId: 9, bandeira: 'VISA', taxaPercentual: 3.25,
  };
  const selected = selectPaymentRule([general, specific], {
    formaPagamento: 'CARTAO_CREDITO', parcelas: 2, adquirenteId: 9, bandeira: 'visa',
  });
  assert.equal(selected?.id, 2);
});

test('calcula taxa percentual mais taxa fixa com arredondamento monetario', () => {
  assert.equal(calculatePaymentFee(139.8, { ...general, taxaPercentual: 3.5, taxaFixa: 0.5 }), 5.39);
});

test('aceita split somente quando a soma fecha exatamente o total monetario', () => {
  assert.equal(paymentTotalsMatch(100, [40, 60]), true);
  assert.equal(paymentTotalsMatch(100, [40, 59.99]), false);
});

test('calcula troco sem permitir resultado negativo', () => {
  assert.equal(calculateCashChange(37.5, 50), 12.5);
  assert.equal(calculateCashChange(37.5, 30), 0);
});
