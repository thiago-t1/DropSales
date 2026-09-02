export interface LoginRequest { email: string; senha: string; }
export interface LoginResponse { token: string; nome: string; email: string; perfil: string; }
export interface RegisterRequest { nome: string; nomeEmpresa?: string; email: string; senha: string; }

export interface Produto {
  id: number; nome: string; descricao: string | null; sku: string;
  precoCusto: number; precoVenda: number;
  quantidadeEstoque: number; estoqueMinimo: number;
  categoriaId: number | null; categoria: string | null; estoqueBaixo: boolean;
}
export interface ProdutoRequest {
  nome: string; descricao: string; sku: string;
  precoCusto: number; precoVenda: number;
  quantidadeEstoque: number; estoqueMinimo: number; categoriaId?: number;
}

export interface ItemVendaRequest { produtoId: number; quantidade: number; }
export type FormaPagamento = 'DINHEIRO' | 'PIX' | 'CARTAO_DEBITO' | 'CARTAO_CREDITO';
export type FormaPagamentoVenda = FormaPagamento | 'MISTO';
export interface PagamentoVendaRequest {
  formaPagamento: FormaPagamento;
  valor: number;
  adquirenteId?: number | null;
  bandeira?: string | null;
  parcelas?: number;
  valorRecebido?: number | null;
}
export interface VendaRequest {
  observacao?: string;
  itens: ItemVendaRequest[];
  formaPagamento: FormaPagamento;
  taxaPagamentoPercentual: number;
  pagamentos?: PagamentoVendaRequest[];
}
export interface VendaItemResponse {
  produtoId: number; produto: string;
  quantidade: number; precoUnitario: number; subtotal: number;
}
export type VendaStatus = 'CONCLUIDA' | 'CANCELADA';
export interface VendaAuditoriaResponse {
  tipo: string;
  responsavel: string;
  descricao: string;
  criadoEm: string;
}
export interface VendaResponse {
  id: number; idempotencyKey: string; vendedor: string; total: number;
  observacao: string; criadoEm: string; itens: VendaItemResponse[];
  formaPagamento: FormaPagamentoVenda;
  taxaPagamentoPercentual: number;
  taxaPagamentoValor: number;
  valorLiquido: number;
  status: VendaStatus;
  canceladaEm: string | null;
  canceladaPor: string | null;
  motivoCancelamento: string | null;
  auditorias: VendaAuditoriaResponse[];
  pagamentos?: PagamentoVendaResponse[];
}

export interface PagamentoVendaResponse {
  id: number;
  formaPagamento: FormaPagamento;
  adquirenteId: number | null;
  adquirenteNome: string | null;
  bandeira: string | null;
  parcelas: number;
  valorBruto: number;
  taxaPercentual: number;
  taxaFixa: number;
  taxaValor: number;
  valorLiquido: number;
  valorRecebido: number | null;
  troco: number | null;
  prazoRecebimentoDias: number;
  status?: 'ATIVO' | 'SUBSTITUIDO' | 'CANCELADO';
}

export interface VendaDiaria { data: string; total: number; }
export interface TopProduto { nome: string; totalUnidades: number; }
export interface VendaRecente {
  id: number; vendedor: string; data: string; valor: number; totalItens: number;
}
export interface DashboardResponse {
  saldo: number; receitas: number; despesas: number;
  receitaBruta?: number; saldoOperacional?: number;
  lucroLiquido: number; lucroBruto?: number; cmv: number; taxasPagamento: number;
  recebidoLiquido?: number; aReceber?: number; areceber?: number;
  estoqueBaixo: Produto[];
  vendasDiarias: VendaDiaria[];
  custosDiarios: VendaDiaria[];
  topProdutos: TopProduto[];
  vendasRecentes: VendaRecente[];
}

export interface UsuarioResponse {
  id: number; nome: string; email: string; perfil: string; temFoto: boolean; token?: string;
}
export interface UsuarioUpdateRequest { nome: string; email: string; }
export interface AlterarSenhaRequest { senhaAtual: string; novaSenha: string; confirmarSenha: string; }

export interface ImportResultDTO {
  importados: number; atualizados: number; ignorados: number; erros: string[];
}
