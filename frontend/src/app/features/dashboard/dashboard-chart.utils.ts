import type { VendaDiaria } from '../../core/models/api.models';

export interface PontoSerieFinanceira {
  data: string;
  receita: number;
  cmv: number;
}

export function prepararSerieAtiva(
  vendas: VendaDiaria[],
  custos: VendaDiaria[],
  minimoDias = 7,
): PontoSerieFinanceira[] {
  const receitasPorData = new Map(vendas.map((item) => [item.data, Number(item.total) || 0]));
  const custosPorData = new Map(custos.map((item) => [item.data, Number(item.total) || 0]));
  const datas = [...new Set([...vendas.map((item) => item.data), ...custos.map((item) => item.data)])];
  const serie = datas.map((data) => ({
    data,
    receita: receitasPorData.get(data) ?? 0,
    cmv: custosPorData.get(data) ?? 0,
  }));

  if (serie.length <= minimoDias) return serie;
  const primeiraMovimentacao = serie.findIndex((ponto) => ponto.receita > 0 || ponto.cmv > 0);
  const inicioMinimo = serie.length - minimoDias;
  const inicio = primeiraMovimentacao < 0
    ? inicioMinimo
    : Math.min(primeiraMovimentacao, inicioMinimo);
  return serie.slice(inicio);
}

export function formatarRotuloData(valor: string): string {
  if (!valor) return '—';
  if (/^\d{2}\/\d{2}$/.test(valor)) return valor;
  if (/^\d{2}\/\d{2}\/\d{4}\s+\d{2}:\d{2}$/.test(valor)) {
    return valor.replace(/\s+/, ' às ');
  }
  const normalizado = valor.includes('T') ? valor : valor.replace(' ', 'T');
  const data = new Date(normalizado);
  if (Number.isNaN(data.getTime())) return valor.split(' ')[0];
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
  }).format(data);
}
