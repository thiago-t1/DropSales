export interface LoginRequest { email: string; senha: string; }
export interface LoginResponse { token: string; nome: string; email: string; perfil: string; }
export interface RegisterRequest { nome: string; email: string; senha: string; }

export interface Produto {
  id: number; nome: string; sku: string;
  precoCusto: number; precoVenda: number;
  quantidadeEstoque: number; estoqueMinimo: number;
  categoria: string; estoqueBaixo: boolean;
}
export interface ProdutoRequest {
  nome: string; descricao: string; sku: string;
  precoCusto: number; precoVenda: number;
  quantidadeEstoque: number; estoqueMinimo: number; categoriaId?: number;
}

export interface ItemVendaRequest { produtoId: number; quantidade: number; }
export interface VendaRequest { observacao?: string; itens: ItemVendaRequest[]; }
export interface VendaItemResponse {
  produtoId: number; produto: string;
  quantidade: number; precoUnitario: number; subtotal: number;
}
export interface VendaResponse {
  id: number; vendedor: string; total: number;
  observacao: string; criadoEm: string; itens: VendaItemResponse[];
}

export interface VendaDiaria { data: string; total: number; }
export interface TopProduto { nome: string; totalUnidades: number; }
export interface VendaRecente {
  id: number; vendedor: string; data: string; valor: number; totalItens: number;
}
export interface DashboardResponse {
  saldo: number; receitas: number; despesas: number;
  lucroLiquido: number; cmv: number;
  estoqueBaixo: Produto[];
  vendasDiarias: VendaDiaria[];
  custosDiarios: VendaDiaria[];
  topProdutos: TopProduto[];
  vendasRecentes: VendaRecente[];
}

export interface UsuarioResponse {
  id: number; nome: string; email: string; perfil: string; temFoto: boolean;
}
export interface UsuarioUpdateRequest { nome: string; email: string; }
export interface AlterarSenhaRequest { senhaAtual: string; novaSenha: string; confirmarSenha: string; }

export interface ImportResultDTO {
  importados: number; atualizados: number; ignorados: number; erros: string[];
}