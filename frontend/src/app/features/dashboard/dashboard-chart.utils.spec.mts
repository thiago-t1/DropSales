import test from 'node:test';
import assert from 'node:assert/strict';
import { formatarRotuloData, prepararSerieAtiva } from './dashboard-chart.utils.ts';

test('preserva dd/MM sem inverter dia e mes', () => {
  assert.equal(formatarRotuloData('06/08'), '06/08');
});

test('remove semanas vazias anteriores e conserva sete dias de contexto', () => {
  const vendas = Array.from({ length: 30 }, (_, indice) => ({
    data: `${String(indice + 1).padStart(2, '0')}/08`,
    total: indice === 29 ? 500 : 0,
  }));
  const custos = vendas.map((item) => ({ ...item, total: item.total ? 100 : 0 }));

  const serie = prepararSerieAtiva(vendas, custos);

  assert.equal(serie.length, 7);
  assert.equal(serie[0].data, '24/08');
  assert.deepEqual(serie.at(-1), { data: '30/08', receita: 500, cmv: 100 });
});

test('alinha receita e cmv por data mesmo quando as listas diferem', () => {
  const serie = prepararSerieAtiva(
    [{ data: '03/09', total: 200 }],
    [{ data: '04/09', total: 50 }],
  );

  assert.deepEqual(serie, [
    { data: '03/09', receita: 200, cmv: 0 },
    { data: '04/09', receita: 0, cmv: 50 },
  ]);
});
