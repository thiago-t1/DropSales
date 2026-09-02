import type { FormaPagamento } from '../../core/models/api.models';
import type { Recebivel } from '../../core/models/business.models';

export type CaixaSecao = 'visao-geral' | 'a-receber' | 'movimentacoes' | 'fechamento';

export interface TotalPorForma {
  formaPagamento: FormaPagamento;
  quantidade: number;
  valorBruto: number;
  taxaValor: number;
  valorLiquido: number;
}

export interface ResumoFechamento {
  quantidade: number;
  valorBruto: number;
  taxaValor: number;
  valorLiquido: number;
  dinheiro: number;
  pix: number;
  cartoes: number;
  formas: TotalPorForma[];
}

export function normalizarSecaoCaixa(valor: string | null | undefined): CaixaSecao {
  const secoes: CaixaSecao[] = ['visao-geral', 'a-receber', 'movimentacoes', 'fechamento'];
  return secoes.includes(valor as CaixaSecao) ? valor as CaixaSecao : 'visao-geral';
}

export function dataNoFuso(data: string | Date, timezone: string): string {
  const valor = data instanceof Date ? data : new Date(data);
  if (Number.isNaN(valor.getTime())) return '';

  let formatador: Intl.DateTimeFormat;
  try {
    formatador = new Intl.DateTimeFormat('pt-BR', {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
  } catch {
    formatador = new Intl.DateTimeFormat('pt-BR', {
      timeZone: 'America/Sao_Paulo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
  }
  const partes = formatador.formatToParts(valor);
  const ano = partes.find((parte) => parte.type === 'year')?.value;
  const mes = partes.find((parte) => parte.type === 'month')?.value;
  const dia = partes.find((parte) => parte.type === 'day')?.value;
  return ano && mes && dia ? `${ano}-${mes}-${dia}` : '';
}

export function agruparPorForma(recebiveis: Recebivel[]): TotalPorForma[] {
  const grupos = new Map<FormaPagamento, TotalPorForma>();
  for (const recebivel of recebiveis) {
    const atual = grupos.get(recebivel.formaPagamento) ?? {
      formaPagamento: recebivel.formaPagamento,
      quantidade: 0,
      valorBruto: 0,
      taxaValor: 0,
      valorLiquido: 0,
    };
    atual.quantidade += 1;
    atual.valorBruto += Number(recebivel.valorBruto || 0);
    atual.taxaValor += Number(recebivel.taxaValor || 0);
    atual.valorLiquido += Number(recebivel.valorLiquido || 0);
    grupos.set(recebivel.formaPagamento, atual);
  }
  return [...grupos.values()]
    .map((grupo) => ({
      ...grupo,
      valorBruto: arredondarMoeda(grupo.valorBruto),
      taxaValor: arredondarMoeda(grupo.taxaValor),
      valorLiquido: arredondarMoeda(grupo.valorLiquido),
    }))
    .sort((a, b) => b.valorLiquido - a.valorLiquido);
}

export function calcularResumoFechamento(
  recebiveis: Recebivel[],
  data: string,
  timezone: string,
): ResumoFechamento {
  const confirmadosDoDia = recebiveis.filter((item) =>
    item.status === 'RECEBIDO'
    && !!item.recebidoEm
    && dataNoFuso(item.recebidoEm, timezone) === data,
  );
  const formas = agruparPorForma(confirmadosDoDia);
  const total = (campo: keyof Pick<TotalPorForma, 'valorBruto' | 'taxaValor' | 'valorLiquido'>) =>
    arredondarMoeda(formas.reduce((soma, forma) => soma + forma[campo], 0));
  const valorDaForma = (forma: FormaPagamento) =>
    formas.find((item) => item.formaPagamento === forma)?.valorLiquido ?? 0;

  return {
    quantidade: confirmadosDoDia.length,
    valorBruto: total('valorBruto'),
    taxaValor: total('taxaValor'),
    valorLiquido: total('valorLiquido'),
    dinheiro: valorDaForma('DINHEIRO'),
    pix: valorDaForma('PIX'),
    cartoes: arredondarMoeda(
      valorDaForma('CARTAO_DEBITO') + valorDaForma('CARTAO_CREDITO'),
    ),
    formas,
  };
}

function arredondarMoeda(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 100) / 100;
}
