import { FormaPagamento } from './api.models';

export type PapelEmpresa = 'PROPRIETARIO' | 'ADMINISTRADOR' | 'GERENTE' | 'OPERADOR';
export type StatusConvite = 'PENDENTE' | 'ACEITO' | 'REVOGADO' | 'EXPIRADO';
export type StatusRecebivel = 'PENDENTE' | 'RECEBIDO' | 'CANCELADO';

export interface LojaResumo {
  id: number;
  nome: string;
  timezone: string;
}

export interface EmpresaResumo {
  id: number;
  nome: string;
  documento: string | null;
  papel: PapelEmpresa;
  lojas: LojaResumo[];
}

export interface ContextoLoja {
  usuarioId: number;
  usuarioNome: string;
  usuarioEmail: string;
  empresaAtualId: number;
  lojaAtualId: number;
  papelAtual: PapelEmpresa;
  empresas: EmpresaResumo[];
}

export interface MembroEmpresa {
  id: number;
  usuarioId: number;
  nome: string;
  email: string;
  papel: PapelEmpresa;
  ativo: boolean;
  usuarioAtual: boolean;
  desde: string;
}

export interface ConviteEmpresa {
  id: number;
  empresaId: number;
  lojaId: number | null;
  empresaNome: string;
  email: string;
  papel: PapelEmpresa;
  status: StatusConvite;
  expiraEm: string;
  criadoEm: string;
  token?: string | null;
}

export interface Adquirente {
  id: number;
  nome: string;
  ativo: boolean;
}

export interface ConfiguracaoTaxa {
  id: number;
  formaPagamento: FormaPagamento;
  adquirenteId: number | null;
  adquirenteNome: string | null;
  bandeira: string | null;
  parcelas: number;
  taxaPercentual: number;
  taxaFixa: number;
  prazoRecebimentoDias: number;
  ativo: boolean;
}

export interface ConfiguracaoTaxaPayload {
  formaPagamento: FormaPagamento;
  adquirenteId: number | null;
  bandeira: string | null;
  parcelas: number;
  taxaPercentual: number;
  taxaFixa: number;
  prazoRecebimentoDias: number;
  ativo: boolean;
}

export interface PagamentoVendaPayload {
  formaPagamento: FormaPagamento;
  valor: number;
  adquirenteId?: number | null;
  bandeira?: string | null;
  parcelas?: number;
  valorRecebido?: number | null;
}

export interface PagamentoVendaDetalhe {
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
}

export interface Recebivel {
  id: number;
  vendaId: number;
  formaPagamento: FormaPagamento;
  adquirente: string | null;
  numeroParcela: number;
  totalParcelas: number;
  valorBruto: number;
  taxaValor: number;
  valorLiquido: number;
  dataPrevista: string;
  status: StatusRecebivel;
  recebidoEm: string | null;
}

export interface ResumoRecebiveis {
  pendente: number;
  recebido: number;
  recebiveis: Recebivel[];
}
