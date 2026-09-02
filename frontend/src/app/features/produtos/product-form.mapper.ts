import type { Produto, ProdutoRequest } from '../../core/models/api.models';

/**
 * Converte o snapshot retornado pela API no payload completo de edicao.
 * Manter esta conversao centralizada evita que campos nao visiveis sejam
 * apagados quando o lojista altera apenas preco ou estoque.
 */
export function produtoParaFormulario(produto: Produto): ProdutoRequest {
  return {
    nome: produto.nome,
    descricao: produto.descricao ?? '',
    sku: produto.sku,
    precoCusto: produto.precoCusto,
    precoVenda: produto.precoVenda,
    quantidadeEstoque: produto.quantidadeEstoque,
    estoqueMinimo: produto.estoqueMinimo,
    categoriaId: produto.categoriaId ?? undefined,
  };
}

export function valorMonetarioValido(valor: number, minimo: number): boolean {
  const centavos = valor * 100;
  return Number.isFinite(valor)
    && valor >= minimo
    && valor <= 9_999_999_999.99
    && Math.abs(centavos - Math.round(centavos)) < 1e-6;
}

export function inteiroNaoNegativo(valor: number): boolean {
  return Number.isInteger(valor) && valor >= 0;
}

export function produtoRequestValido(produto: ProdutoRequest): boolean {
  return Boolean(produto.nome?.trim())
    && produto.nome.trim().length <= 200
    && (produto.descricao?.length ?? 0) <= 500
    && (produto.sku?.length ?? 0) <= 50
    && valorMonetarioValido(Number(produto.precoCusto), 0)
    && valorMonetarioValido(Number(produto.precoVenda), 0.01)
    && inteiroNaoNegativo(Number(produto.quantidadeEstoque))
    && inteiroNaoNegativo(Number(produto.estoqueMinimo));
}
