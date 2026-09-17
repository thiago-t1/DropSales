const NOME_PRODUTO_INVALIDO = /[^\p{L}\p{N} .,'’&/()\-+]/gu;
const NOME_PRODUTO_INVALIDO_TESTE = /[^\p{L}\p{N} .,'’&/()\-+]/u;

export function sanitizarNomeProduto(valor: string): string {
  return valor.replace(NOME_PRODUTO_INVALIDO, '').replace(/\s{2,}/g, ' ').slice(0, 200);
}

export function nomeProdutoValido(valor: string): boolean {
  return Boolean(valor.trim())
    && valor.trim().length <= 200
    && !NOME_PRODUTO_INVALIDO_TESTE.test(valor);
}

export function proximoCodigoProduto(codigos: Array<string | null | undefined>): string {
  const maior = codigos.reduce((atual, codigo) => {
    const resultado = /^ITEM-(\d+)$/i.exec(codigo?.trim() ?? '');
    return resultado ? Math.max(atual, Number(resultado[1])) : atual;
  }, 0);
  return `ITEM-${String(maior + 1).padStart(4, '0')}`;
}

export function lerMoedaBrasileira(valor: string): number | null {
  const limpo = valor.replace(/[^\d,.]/g, '').replace(/\./g, '').replace(',', '.');
  if (!limpo || limpo === '.') return 0;
  const numero = Number(limpo);
  return Number.isFinite(numero) ? Math.round(numero * 100) / 100 : null;
}

export function formatarMoedaBrasileira(valor: number): string {
  if (!Number.isFinite(valor)) return '';
  return valor.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function lerInteiroFormatado(valor: string): number | null {
  const digitos = valor.replace(/\D/g, '');
  if (!digitos) return 0;
  const numero = Number(digitos);
  return Number.isSafeInteger(numero) ? numero : null;
}

export function formatarInteiroBrasileiro(valor: number): string {
  if (!Number.isSafeInteger(valor) || valor < 0) return '';
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
}
