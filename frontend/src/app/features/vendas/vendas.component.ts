import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import type { PendingChangesAware } from '../../core/guards/pending-changes.guard';
import {
  FormaPagamento,
  FormaPagamentoVenda,
  ItemVendaRequest,
  PagamentoVendaRequest,
  Produto,
  VendaRequest,
  VendaResponse,
} from '../../core/models/api.models';
import { Adquirente, ConfiguracaoTaxa } from '../../core/models/business.models';
import { BusinessApiService } from '../../core/services/business-api.service';
import { TenantService } from '../../core/services/tenant.service';
import {
  calculateCashChange,
  calculatePaymentFee,
  paymentTotalsMatch,
  roundMoney,
  selectPaymentRule,
} from './payment-checkout.utils';

interface CartItem {
  produto: Produto;
  quantidade: number;
  precoUnitario: number;
  subtotal: number;
}

interface FormaPagamentoOption {
  value: FormaPagamento;
  label: string;
  resumo: string;
}

interface CheckoutPagamento extends PagamentoVendaRequest {
  localId: number;
}

@Component({
  selector: 'app-vendas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './vendas.component.html',
  styles: [`
    :host {
      display: block;
      color: var(--ds-text);
      --sales-ink: var(--ds-text);
      --sales-muted: var(--ds-muted);
      --sales-border: var(--ds-border);
      --sales-border-soft: #edf0f5;
      --sales-soft: var(--ds-surface-soft);
      --sales-surface: var(--ds-surface);
      --sales-hover: #f8faff;
      --sales-indigo: #4f46e5;
      --sales-indigo-soft: var(--ds-primary-soft);
      --sales-success-text: #047857;
      --sales-success-soft: #ecfdf5;
      --sales-success-border: #d1fae5;
      --sales-warning-text: #b45309;
      --sales-warning-soft: #fffbeb;
      --sales-warning-border: #fde68a;
      --sales-danger-text: #b91c1c;
      --sales-danger-soft: #fef2f2;
      --sales-danger-border: #fecaca;
    }

    :host-context(.dark) {
      --sales-border-soft: #202d40;
      --sales-hover: #19243a;
      --sales-indigo: #818cf8;
      --sales-success-text: var(--ds-success-text);
      --sales-success-soft: var(--ds-success-soft);
      --sales-success-border: var(--ds-success-border);
      --sales-warning-text: var(--ds-warning-text);
      --sales-warning-soft: var(--ds-warning-soft);
      --sales-warning-border: var(--ds-warning-border);
      --sales-danger-text: var(--ds-danger-text);
      --sales-danger-soft: var(--ds-danger-soft);
      --sales-danger-border: var(--ds-danger-border);
    }

    .step-dot {
      display: inline-flex;
      width: 28px;
      height: 28px;
      align-items: center;
      justify-content: center;
      border-radius: 9px;
      background: var(--sales-indigo-soft);
      color: var(--sales-indigo);
      font-size: 0.75rem;
      font-weight: 800;
    }

    .sales-input {
      width: 100%;
      min-height: 44px;
      padding: 10px 13px;
      color: var(--sales-ink);
      background: var(--sales-surface);
      border: 1px solid var(--ds-border-strong);
      border-radius: 12px;
      outline: none;
      transition: border-color 160ms ease, box-shadow 160ms ease;
    }

    .sales-input::placeholder {
      color: var(--ds-muted-soft);
    }

    .sales-input:focus {
      border-color: #818cf8;
      box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.11);
    }

    .product-list {
      max-height: 300px;
      overflow: auto;
      border: 1px solid var(--sales-border);
      border-radius: 15px;
      background: var(--sales-surface);
    }

    .product-option {
      width: 100%;
      padding: 12px 14px;
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 14px;
      color: var(--sales-ink);
      text-align: left;
      border-bottom: 1px solid var(--sales-border-soft);
      transition: background 150ms ease, box-shadow 150ms ease;
    }

    .product-option:last-child {
      border-bottom: 0;
    }

    .product-option:hover:not(:disabled) {
      background: var(--sales-hover);
    }

    .product-option.is-selected {
      position: relative;
      z-index: 1;
      background: var(--sales-indigo-soft);
      box-shadow: inset 3px 0 0 var(--sales-indigo);
    }

    .product-option:disabled {
      cursor: not-allowed;
      opacity: 0.56;
    }

    .stock-chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 4px 8px;
      color: var(--sales-success-text);
      background: var(--sales-success-soft);
      border: 1px solid var(--sales-success-border);
      border-radius: 999px;
      font-size: 0.68rem;
      font-weight: 700;
      white-space: nowrap;
    }

    .stock-chip.is-low {
      color: var(--sales-warning-text);
      background: var(--sales-warning-soft);
      border-color: var(--sales-warning-border);
    }

    .stock-chip.is-empty {
      color: var(--sales-danger-text);
      background: var(--sales-danger-soft);
      border-color: var(--sales-danger-border);
    }

    .quantity-control {
      height: 44px;
      display: inline-grid;
      grid-template-columns: 38px minmax(48px, 1fr) 38px;
      align-items: center;
      overflow: hidden;
      background: var(--sales-surface);
      border: 1px solid var(--ds-border-strong);
      border-radius: 12px;
    }

    .quantity-control button {
      height: 100%;
      display: grid;
      place-items: center;
      color: var(--sales-muted);
      font-weight: 700;
      transition: color 150ms ease, background 150ms ease;
    }

    .quantity-control button:hover:not(:disabled) {
      color: var(--sales-indigo);
      background: var(--sales-indigo-soft);
    }

    .quantity-control button:disabled {
      color: var(--ds-muted-soft);
      cursor: not-allowed;
    }

    .quantity-control input {
      width: 100%;
      color: var(--sales-ink);
      font-weight: 700;
      text-align: center;
      background: transparent;
      border: 0;
      outline: none;
      -moz-appearance: textfield;
    }

    .quantity-control input::-webkit-inner-spin-button,
    .quantity-control input::-webkit-outer-spin-button {
      margin: 0;
      -webkit-appearance: none;
    }

    .primary-action {
      min-height: 46px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 9px;
      padding: 11px 18px;
      color: #fff;
      background: linear-gradient(135deg, #4f46e5 0%, #6366f1 62%, #0f9f8f 145%);
      border-radius: 13px;
      box-shadow: 0 9px 20px rgba(79, 70, 229, 0.19);
      font-size: 0.875rem;
      font-weight: 750;
      transition: transform 150ms ease, box-shadow 150ms ease, opacity 150ms ease;
    }

    .primary-action:hover:not(:disabled) {
      box-shadow: 0 12px 26px rgba(79, 70, 229, 0.25);
      transform: translateY(-1px);
    }

    .primary-action:active:not(:disabled) {
      transform: translateY(0);
    }

    .primary-action:disabled {
      cursor: not-allowed;
      opacity: 0.48;
      box-shadow: none;
    }

    .secondary-action {
      min-height: 42px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 9px 14px;
      color: var(--ds-text-soft);
      background: var(--sales-surface);
      border: 1px solid var(--ds-border-strong);
      border-radius: 12px;
      font-size: 0.82rem;
      font-weight: 700;
      transition: border-color 150ms ease, color 150ms ease, background 150ms ease;
    }

    .secondary-action:hover:not(:disabled) {
      color: var(--sales-indigo);
      background: var(--sales-hover);
      border-color: #818cf8;
    }

    .icon-action {
      width: 36px;
      height: 36px;
      display: inline-grid;
      place-items: center;
      color: var(--sales-muted);
      background: var(--sales-surface);
      border: 1px solid var(--sales-border);
      border-radius: 10px;
      transition: color 150ms ease, border-color 150ms ease, background 150ms ease;
    }

    .icon-action:hover {
      color: var(--sales-indigo);
      background: var(--sales-indigo-soft);
      border-color: #818cf8;
    }

    .icon-action:disabled,
    .secondary-action:disabled {
      cursor: not-allowed;
      opacity: 0.45;
    }

    .icon-action:disabled:hover,
    .secondary-action:disabled:hover {
      color: var(--sales-muted);
      background: var(--sales-surface);
      border-color: var(--sales-border);
    }

    .icon-action.is-danger:hover {
      color: var(--sales-danger-text);
      background: var(--sales-danger-soft);
      border-color: var(--sales-danger-border);
    }

    .cart-line {
      display: grid;
      grid-template-columns: minmax(0, 1.55fr) minmax(118px, 0.75fr) minmax(94px, 0.55fr) 40px;
      align-items: center;
      gap: 16px;
      padding: 15px 4px;
      border-bottom: 1px solid var(--sales-border-soft);
    }

    .cart-line:last-child {
      border-bottom: 0;
    }

    .summary-card {
      position: relative;
      overflow: hidden;
      background:
        radial-gradient(circle at 100% 0%, rgba(99, 102, 241, 0.11), transparent 36%),
        var(--sales-surface);
    }

    .summary-card::before {
      position: absolute;
      top: 0;
      right: 0;
      left: 0;
      height: 3px;
      background: linear-gradient(90deg, #4f46e5, #14b8a6);
      content: '';
    }

    .summary-total {
      color: var(--ds-primary-dark);
      font-size: clamp(1.65rem, 3vw, 2.1rem);
      font-weight: 800;
      letter-spacing: -0.035em;
    }

    .history-table {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
    }

    .history-table th {
      padding: 11px 14px;
      color: var(--ds-muted);
      background: var(--sales-soft);
      border-top: 1px solid var(--sales-border-soft);
      border-bottom: 1px solid var(--sales-border-soft);
      font-size: 0.68rem;
      font-weight: 800;
      letter-spacing: 0.07em;
      text-transform: uppercase;
    }

    .history-table td {
      padding: 15px 14px;
      color: var(--ds-text-soft);
      border-bottom: 1px solid var(--sales-border-soft);
      font-size: 0.82rem;
    }

    .history-table tbody tr {
      transition: background 150ms ease;
    }

    .history-table tbody tr:hover {
      background: var(--sales-hover);
    }

    .history-table tbody tr.is-editing {
      background: var(--sales-indigo-soft);
      box-shadow: inset 3px 0 0 var(--sales-indigo);
    }

    .sale-id {
      display: inline-flex;
      align-items: center;
      padding: 4px 8px;
      color: var(--sales-indigo);
      background: var(--sales-indigo-soft);
      border-radius: 8px;
      font-size: 0.72rem;
      font-weight: 800;
    }

    .history-mobile-card {
      padding: 15px;
      background: var(--sales-surface);
      border: 1px solid var(--sales-border);
      border-radius: 16px;
    }

    .history-mobile-card.is-editing {
      background: var(--sales-indigo-soft);
      border-color: #6366f1;
      box-shadow: inset 3px 0 0 var(--sales-indigo);
    }

    @media (max-width: 639px) {
      .product-list {
        max-height: 248px;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .primary-action,
      .product-option,
      .icon-action {
        transition: none;
      }
    }
  `],
})
export class VendasComponent implements OnInit, PendingChangesAware {
  produtos: Produto[] = [];
  vendas: VendaResponse[] = [];

  carrinho: CartItem[] = [];
  produtoSelecionadoId: number | null = null;
  quantidade = 1;
  observacao = '';
  formaPagamento: FormaPagamento = 'PIX';
  taxaPagamentoPercentual = 0;
  private proximoPagamentoId = 1;
  pagamentosCheckout: CheckoutPagamento[] = [this.novoPagamento('PIX', 0)];
  pagamentoDividido = false;
  configuracoesTaxa: ConfiguracaoTaxa[] = [];
  adquirentes: Adquirente[] = [];
  buscaProduto = '';
  buscaHistorico = '';

  readonly formasPagamento: FormaPagamentoOption[] = [
    { value: 'PIX', label: 'Pix', resumo: 'Recebimento imediato' },
    { value: 'DINHEIRO', label: 'Dinheiro', resumo: 'Sem taxa de operação' },
    { value: 'CARTAO_DEBITO', label: 'Débito', resumo: 'Taxa da adquirente' },
    { value: 'CARTAO_CREDITO', label: 'Crédito', resumo: 'Taxa da adquirente' },
  ];

  vendaEmEdicaoId: number | null = null;
  private quantidadeOriginalPorProduto = new Map<number, number>();

  loading = false;
  loadingVendas = true;
  sucesso = '';
  erro = '';
  private feedbackTimer?: ReturnType<typeof setTimeout>;

  showDetalheModal = false;
  vendaDetalhe: VendaResponse | null = null;

  showCancelamentoModal = false;
  vendaParaCancelar: VendaResponse | null = null;
  motivoCancelamento = '';
  erroCancelamento = '';
  cancelando = false;

  checkoutIncerto = false;
  private idempotencyKey: string | null = null;
  private payloadPostPendente: VendaRequest | null = null;

  constructor(
    private apiService: ApiService,
    private businessApi: BusinessApiService,
    readonly tenantService: TenantService,
  ) {}

  ngOnInit(): void {
    this.carregarProdutos();
    this.carregarVendas();
    this.carregarConfiguracoesPagamento();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.showCancelamentoModal) {
      this.fecharCancelamento();
      return;
    }
    if (this.showDetalheModal) this.fecharDetalhes();
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (!this.temAlteracoesNaoSalvas) return;
    event.preventDefault();
    event.returnValue = '';
  }

  canDeactivate(): boolean {
    if (this.loading || this.cancelando) return false;
    if (!this.temAlteracoesNaoSalvas) return true;
    return window.confirm(
      'Existe uma venda em andamento. Deseja sair e descartar o checkout atual?',
    );
  }

  carregarProdutos(): void {
    this.apiService.getProdutos().subscribe({
      next: (produtos) => (this.produtos = produtos),
      error: () => this.mostrarErro('Não foi possível carregar os produtos. Tente novamente.'),
    });
  }

  carregarVendas(): void {
    this.loadingVendas = true;
    this.apiService.getVendas().subscribe({
      next: (vendas) => {
        this.vendas = vendas;
        this.loadingVendas = false;
      },
      error: () => {
        this.loadingVendas = false;
        this.mostrarErro('Não foi possível carregar o histórico de vendas.');
      },
    });
  }

  carregarConfiguracoesPagamento(): void {
    this.businessApi.listarTaxas().subscribe({
      next: (taxas) => {
        this.configuracoesTaxa = taxas.filter((taxa) => taxa.ativo);
        this.sincronizarPagamentoPrincipal();
      },
      error: () => this.mostrarErro('Não foi possível carregar as taxas da loja.'),
    });
    this.businessApi.listarAdquirentes().subscribe({
      next: (adquirentes) => (this.adquirentes = adquirentes),
      error: () => {},
    });
  }

  get produtoSelecionado(): Produto | undefined {
    return this.produtos.find((produto) => produto.id === this.produtoSelecionadoId);
  }

  get produtosFiltrados(): Produto[] {
    const termo = this.normalizar(this.buscaProduto);
    const produtos = termo
      ? this.produtos.filter((produto) =>
          this.normalizar(`${produto.nome} ${produto.sku} ${produto.categoria || ''}`).includes(termo),
        )
      : this.produtos;

    return [...produtos].sort((a, b) => {
      const aDisponivel = this.estoqueDisponivel(a) > 0 ? 0 : 1;
      const bDisponivel = this.estoqueDisponivel(b) > 0 ? 0 : 1;
      return aDisponivel - bDisponivel || a.nome.localeCompare(b.nome, 'pt-BR');
    });
  }

  get vendasFiltradas(): VendaResponse[] {
    const termo = this.normalizar(this.buscaHistorico);
    if (!termo) return this.vendas;

    return this.vendas.filter((venda) =>
      this.normalizar(
        `${venda.id} ${venda.vendedor} ${this.pagamentoVendaLabel(venda)} ${this.statusLabel(venda)} ${venda.motivoCancelamento || ''} ${venda.observacao || ''} ${venda.itens.map((item) => item.produto).join(' ')}`,
      ).includes(termo),
    );
  }

  get totalCarrinho(): number {
    return this.carrinho.reduce((total, item) => total + item.subtotal, 0);
  }

  get totalItensCarrinho(): number {
    return this.carrinho.reduce((total, item) => total + item.quantidade, 0);
  }

  get totalHistorico(): number {
    return this.vendas
      .filter((venda) => !this.vendaCancelada(venda))
      .reduce((total, venda) => total + Number(venda.total), 0);
  }

  get totalVendasConcluidas(): number {
    return this.vendas.filter((venda) => !this.vendaCancelada(venda)).length;
  }

  get taxaPagamentoValida(): boolean {
    const pagamentos = this.pagamentosParaEnvio();
    if (pagamentos.length === 0 || pagamentos.some((item) => Number(item.valor) <= 0)) return false;
    if (!paymentTotalsMatch(this.totalCarrinho, pagamentos.map((item) => item.valor))) return false;
    return this.pagamentosCheckout.every((item) => {
      if (!this.configuracaoPagamento(item)) return false;
      if (item.formaPagamento !== 'DINHEIRO' || item.valorRecebido == null) return true;
      return Number(item.valorRecebido) >= this.valorEfetivoPagamento(item);
    });
  }

  get taxaPagamentoValor(): number {
    return this.arredondarMoeda(
      this.pagamentosCheckout.reduce((total, item) => total + this.taxaValorPagamento(item), 0),
    );
  }

  get valorLiquidoVenda(): number {
    return this.arredondarMoeda(Math.max(0, this.totalCarrinho - this.taxaPagamentoValor));
  }

  get totalPagamentos(): number {
    if (!this.pagamentoDividido && this.pagamentosCheckout.length === 1) return this.totalCarrinho;
    return this.arredondarMoeda(
      this.pagamentosCheckout.reduce((total, item) => total + Number(item.valor || 0), 0),
    );
  }

  get valorRestantePagamento(): number {
    return this.arredondarMoeda(this.totalCarrinho - this.totalPagamentos);
  }

  get erroPagamento(): string {
    if (!this.carrinho.length) return '';
    if (this.pagamentosCheckout.some((item) => this.valorEfetivoPagamento(item) <= 0)) {
      return 'Informe um valor maior que zero em cada pagamento.';
    }
    if (Math.abs(this.valorRestantePagamento) > 0.009) {
      return this.valorRestantePagamento > 0
        ? `Ainda faltam R$ ${this.fmt(this.valorRestantePagamento)} para fechar o total.`
        : `Os pagamentos excedem o total em R$ ${this.fmt(Math.abs(this.valorRestantePagamento))}.`;
    }
    if (this.pagamentosCheckout.some((item) => !this.configuracaoPagamento(item))) {
      return 'Não há uma regra de taxa ativa para uma das combinações escolhidas.';
    }
    const dinheiroInvalido = this.pagamentosCheckout.some((item) =>
      item.formaPagamento === 'DINHEIRO'
      && item.valorRecebido != null
      && Number(item.valorRecebido) < this.valorEfetivoPagamento(item),
    );
    return dinheiroInvalido ? 'O valor recebido em dinheiro é menor que o valor a cobrar.' : '';
  }

  get modoEdicao(): boolean {
    return this.vendaEmEdicaoId !== null;
  }

  get checkoutBloqueado(): boolean {
    return this.loading || this.checkoutIncerto;
  }

  get temAlteracoesNaoSalvas(): boolean {
    return this.carrinho.length > 0 || this.modoEdicao || this.checkoutIncerto;
  }

  selecionarFormaPagamento(formaPagamento: FormaPagamento): void {
    if (this.checkoutBloqueado) return;
    this.formaPagamento = formaPagamento;
    const principal = this.pagamentosCheckout[0] || this.novoPagamento(formaPagamento, 0);
    principal.formaPagamento = formaPagamento;
    principal.parcelas = 1;
    principal.adquirenteId = null;
    principal.bandeira = null;
    principal.valorRecebido = formaPagamento === 'DINHEIRO' ? this.totalCarrinho : null;
    this.pagamentosCheckout = [principal];
    this.pagamentoDividido = false;
    this.sincronizarPagamentoPrincipal();
    this.limparErro();
  }

  alternarPagamentoDividido(): void {
    if (this.checkoutBloqueado) return;
    this.pagamentoDividido = !this.pagamentoDividido;
    if (this.pagamentoDividido) {
      this.pagamentosCheckout[0].valor = this.totalCarrinho;
    } else {
      const principal = this.pagamentosCheckout[0] || this.novoPagamento('PIX', this.totalCarrinho);
      principal.valor = this.totalCarrinho;
      this.pagamentosCheckout = [principal];
    }
    this.sincronizarPagamentoPrincipal();
  }

  adicionarPagamento(): void {
    if (this.checkoutBloqueado || this.pagamentosCheckout.length >= 5) return;
    const restante = Math.max(0, this.valorRestantePagamento);
    this.pagamentosCheckout = [...this.pagamentosCheckout, this.novoPagamento('PIX', restante)];
  }

  removerPagamento(localId: number): void {
    if (this.checkoutBloqueado || this.pagamentosCheckout.length <= 1) return;
    this.pagamentosCheckout = this.pagamentosCheckout.filter((item) => item.localId !== localId);
    this.sincronizarPagamentoPrincipal();
  }

  formaPagamentoLinhaAlterada(item: CheckoutPagamento): void {
    item.parcelas = 1;
    item.adquirenteId = null;
    item.bandeira = null;
    item.valorRecebido = item.formaPagamento === 'DINHEIRO'
      ? this.valorEfetivoPagamento(item) : null;
    this.sincronizarPagamentoPrincipal();
  }

  detalhesPagamentoAlterados(item: CheckoutPagamento): void {
    if (item.formaPagamento !== 'CARTAO_CREDITO') item.parcelas = 1;
    this.sincronizarPagamentoPrincipal();
  }

  pagamentoLinhaEmCartao(item: CheckoutPagamento): boolean {
    return item.formaPagamento === 'CARTAO_DEBITO' || item.formaPagamento === 'CARTAO_CREDITO';
  }

  valorEfetivoPagamento(item: CheckoutPagamento): number {
    return !this.pagamentoDividido && this.pagamentosCheckout.length === 1
      ? this.totalCarrinho : Number(item.valor || 0);
  }

  taxaPercentualPagamento(item: CheckoutPagamento): number {
    return Number(this.configuracaoPagamento(item)?.taxaPercentual || 0);
  }

  taxaValorPagamento(item: CheckoutPagamento): number {
    const config = this.configuracaoPagamento(item);
    return calculatePaymentFee(this.valorEfetivoPagamento(item), config);
  }

  liquidoPagamento(item: CheckoutPagamento): number {
    return this.arredondarMoeda(Math.max(0, this.valorEfetivoPagamento(item) - this.taxaValorPagamento(item)));
  }

  trocoPagamento(item: CheckoutPagamento): number {
    if (item.formaPagamento !== 'DINHEIRO') return 0;
    return calculateCashChange(this.valorEfetivoPagamento(item), item.valorRecebido);
  }

  selecionarProduto(produto: Produto): void {
    if (this.checkoutBloqueado) return;
    if (this.estoqueRestante(produto) <= 0) return;
    this.produtoSelecionadoId = produto.id;
    this.quantidade = 1;
  }

  alterarQuantidadeSelecao(delta: number): void {
    if (this.checkoutBloqueado) return;
    const produto = this.produtoSelecionado;
    const limite = produto ? this.estoqueRestante(produto) : Number.MAX_SAFE_INTEGER;
    this.quantidade = Math.max(1, Math.min(this.quantidade + delta, Math.max(1, limite)));
  }

  normalizarQuantidadeSelecao(): void {
    if (this.checkoutBloqueado) return;
    const produto = this.produtoSelecionado;
    const limite = produto ? this.estoqueRestante(produto) : Number.MAX_SAFE_INTEGER;
    const valor = Number.isFinite(Number(this.quantidade)) ? Math.floor(Number(this.quantidade)) : 1;
    this.quantidade = Math.max(1, Math.min(valor, Math.max(1, limite)));
  }

  adicionarItem(): void {
    if (this.checkoutBloqueado) return;
    const produto = this.produtoSelecionado;
    this.normalizarQuantidadeSelecao();

    if (!produto) {
      this.mostrarErro('Selecione um produto antes de adicionar.');
      return;
    }

    const existente = this.carrinho.find((item) => item.produto.id === produto.id);
    const novaQuantidade = (existente?.quantidade || 0) + this.quantidade;
    const estoqueDisponivel = this.estoqueDisponivel(produto);

    if (estoqueDisponivel <= 0) {
      this.mostrarErro(`${produto.nome} está sem estoque disponível.`);
      return;
    }

    if (novaQuantidade > estoqueDisponivel) {
      this.mostrarErro(
        `Estoque insuficiente para ${produto.nome}. Disponível: ${estoqueDisponivel} unidade(s).`,
      );
      return;
    }

    if (existente) {
      existente.quantidade = novaQuantidade;
      existente.subtotal = existente.quantidade * existente.precoUnitario;
    } else {
      this.carrinho.push({
        produto,
        quantidade: this.quantidade,
        precoUnitario: Number(produto.precoVenda),
        subtotal: this.quantidade * Number(produto.precoVenda),
      });
    }

    this.produtoSelecionadoId = null;
    this.quantidade = 1;
    this.buscaProduto = '';
    this.limparErro();
  }

  alterarQuantidadeItem(index: number, delta: number): void {
    if (this.checkoutBloqueado) return;
    const item = this.carrinho[index];
    if (!item) return;

    const novaQuantidade = item.quantidade + delta;
    if (novaQuantidade < 1) {
      this.removerItem(index);
      return;
    }

    const estoqueDisponivel = this.estoqueDisponivel(item.produto);
    if (novaQuantidade > estoqueDisponivel) {
      this.mostrarErro(
        `Estoque máximo de ${item.produto.nome}: ${estoqueDisponivel} unidade(s).`,
      );
      return;
    }

    item.quantidade = novaQuantidade;
    item.subtotal = item.quantidade * item.precoUnitario;
    this.limparErro();
  }

  removerItem(index: number): void {
    if (this.checkoutBloqueado) return;
    this.carrinho.splice(index, 1);
  }

  estoqueDisponivel(produto: Produto): number {
    const quantidadeOriginal = this.modoEdicao
      ? this.quantidadeOriginalPorProduto.get(produto.id) || 0
      : 0;
    return Number(produto.quantidadeEstoque) + quantidadeOriginal;
  }

  estoqueRestante(produto: Produto): number {
    const noCarrinho =
      this.carrinho.find((item) => item.produto.id === produto.id)?.quantidade || 0;
    return Math.max(0, this.estoqueDisponivel(produto) - noCarrinho);
  }

  finalizarVenda(): void {
    if (!this.carrinho.length || this.checkoutBloqueado) return;
    if (!this.taxaPagamentoValida) {
      this.mostrarErro(this.erroPagamento || 'Revise os pagamentos antes de finalizar a venda.');
      return;
    }

    this.loading = true;
    this.sucesso = '';
    this.erro = '';

    const itens: ItemVendaRequest[] = this.carrinho.map((item) => ({
      produtoId: item.produto.id,
      quantidade: item.quantidade,
    }));
    const payload: VendaRequest = {
      itens,
      observacao: this.observacao.trim(),
      formaPagamento: this.formaPagamento,
      taxaPagamentoPercentual: this.taxaPagamentoPercentual,
      pagamentos: this.pagamentosParaEnvio(),
    };

    if (this.vendaEmEdicaoId === null) {
      this.payloadPostPendente = {
        ...payload,
        itens: payload.itens.map((item) => ({ ...item })),
      };
      this.enviarNovaVenda(this.payloadPostPendente);
      return;
    }

    const id = this.vendaEmEdicaoId;
    this.apiService.editarVenda(id, payload).subscribe({
      next: (venda) => {
        this.mostrarSucesso(
          `Venda #${venda.id} atualizada. Total: R$ ${this.fmt(venda.total)} · Líquido: R$ ${this.fmt(this.valorLiquidoDaVenda(venda))}`,
        );
        this.resetarFormulario(true);
        this.carregarProdutos();
        this.carregarVendas();
      },
      error: (error) => {
        this.loading = false;
        this.mostrarErro(error.error?.message || error.message || 'Erro ao editar venda.');
      },
    });
  }

  repetirVendaIncerta(): void {
    if (!this.checkoutIncerto || !this.payloadPostPendente || this.loading) return;
    this.checkoutIncerto = false;
    this.loading = true;
    this.sucesso = '';
    this.erro = '';
    this.enviarNovaVenda(this.payloadPostPendente);
  }

  descartarCheckout(): void {
    if (!this.temAlteracoesNaoSalvas) return;
    const descricao = this.modoEdicao ? 'as alterações desta venda' : 'o pedido atual';
    if (!window.confirm(`Deseja descartar ${descricao}? Essa ação não pode ser desfeita.`)) return;
    this.resetarFormulario(true);
  }

  carregarParaEdicao(venda: VendaResponse): void {
    if (!this.podeAlterarVenda()) return;
    if (this.checkoutBloqueado) return;
    if (this.vendaCancelada(venda)) {
      this.mostrarErro(`A Venda #${venda.id} está cancelada e não pode ser editada.`);
      return;
    }
    if (
      this.temAlteracoesNaoSalvas &&
      !window.confirm('Substituir o checkout atual por esta venda? As alterações atuais serão descartadas.')
    ) {
      return;
    }

    this.resetarFormulario(true);
    this.vendaEmEdicaoId = venda.id;
    this.observacao = venda.observacao || '';
    if (venda.pagamentos?.length) {
      this.pagamentoDividido = venda.pagamentos.length > 1;
      this.pagamentosCheckout = venda.pagamentos.map((pagamento) => ({
        localId: this.proximoPagamentoId++,
        formaPagamento: pagamento.formaPagamento,
        valor: Number(pagamento.valorBruto),
        adquirenteId: pagamento.adquirenteId,
        bandeira: pagamento.bandeira,
        parcelas: pagamento.parcelas,
        valorRecebido: pagamento.valorRecebido,
      }));
    } else {
      this.pagamentoDividido = false;
      this.pagamentosCheckout = [{
        localId: this.proximoPagamentoId++,
        formaPagamento: venda.formaPagamento === 'MISTO'
          ? 'PIX'
          : (venda.formaPagamento || 'PIX'),
        valor: Number(venda.total),
        adquirenteId: null,
        bandeira: null,
        parcelas: 1,
        valorRecebido: venda.formaPagamento === 'DINHEIRO' ? Number(venda.total) : null,
      }];
    }
    this.sincronizarPagamentoPrincipal();
    this.carrinho = [];
    this.quantidadeOriginalPorProduto.clear();

    for (const item of venda.itens) {
      this.quantidadeOriginalPorProduto.set(item.produtoId, item.quantidade);
      const produto = this.produtos.find((produtoAtual) => produtoAtual.id === item.produtoId);

      this.carrinho.push({
        produto: produto || {
          id: item.produtoId,
          nome: item.produto,
          descricao: null,
          sku: '',
          precoCusto: 0,
          precoVenda: Number(item.precoUnitario),
          quantidadeEstoque: 0,
          estoqueMinimo: 0,
          categoriaId: null,
          categoria: null,
          estoqueBaixo: false,
        },
        quantidade: item.quantidade,
        precoUnitario: Number(item.precoUnitario),
        subtotal: Number(item.subtotal),
      });
    }

    this.produtoSelecionadoId = null;
    this.quantidade = 1;
    this.buscaProduto = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelarEdicao(): void {
    this.descartarCheckout();
  }

  abrirCancelamento(venda: VendaResponse): void {
    if (!this.podeAlterarVenda()) return;
    if (this.checkoutBloqueado || this.cancelando) return;
    if (this.vendaCancelada(venda)) {
      this.mostrarErro(`A Venda #${venda.id} já está cancelada.`);
      return;
    }
    this.vendaParaCancelar = venda;
    this.motivoCancelamento = '';
    this.erroCancelamento = '';
    this.showCancelamentoModal = true;
  }

  fecharCancelamento(): void {
    if (this.cancelando) return;
    this.showCancelamentoModal = false;
    this.vendaParaCancelar = null;
    this.motivoCancelamento = '';
    this.erroCancelamento = '';
  }

  confirmarCancelamento(): void {
    const venda = this.vendaParaCancelar;
    const motivo = this.motivoCancelamento.trim();
    if (!venda || this.cancelando) return;
    if (!motivo) {
      this.erroCancelamento = 'Informe o motivo do cancelamento.';
      return;
    }

    this.cancelando = true;
    this.erroCancelamento = '';
    this.apiService.cancelarVenda(venda.id, motivo).subscribe({
      next: (vendaCancelada) => {
        this.cancelando = false;
        this.showCancelamentoModal = false;
        this.vendaParaCancelar = null;
        this.motivoCancelamento = '';
        this.mostrarSucesso(`Venda #${venda.id} cancelada com sucesso.`);
        if (this.vendaEmEdicaoId === venda.id) this.resetarFormulario(true);

        if (vendaCancelada) {
          this.substituirVendaNoHistorico(vendaCancelada);
          if (this.vendaDetalhe?.id === vendaCancelada.id) this.vendaDetalhe = vendaCancelada;
        }
        this.carregarProdutos();
        this.carregarVendas();
      },
      error: (error) => {
        this.cancelando = false;
        this.erroCancelamento =
          error.error?.message || error.message || 'Não foi possível cancelar a venda.';
      },
    });
  }

  verDetalhes(venda: VendaResponse): void {
    this.vendaDetalhe = venda;
    this.showDetalheModal = true;
  }

  fecharDetalhes(): void {
    this.showDetalheModal = false;
    this.vendaDetalhe = null;
  }

  vendaCancelada(venda: VendaResponse | null | undefined): boolean {
    return venda?.status === 'CANCELADA';
  }

  statusLabel(venda: VendaResponse | null | undefined): string {
    return this.vendaCancelada(venda) ? 'Cancelada' : 'Concluída';
  }

  auditoriaLabel(tipo: string): string {
    const labels: Record<string, string> = {
      CRIADA: 'Venda criada',
      EDITADA: 'Venda editada',
      CANCELADA: 'Venda cancelada',
    };
    return labels[tipo] || tipo;
  }

  fmt(valor: number | string): string {
    const numero = typeof valor === 'string' ? Number.parseFloat(valor) : valor;
    if (Number.isNaN(numero)) return '0,00';
    return numero.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  fmtData(data: string | null | undefined): string {
    const dataValida = this.criarData(data);
    if (!dataValida) return '-';
    return `${dataValida.toLocaleDateString('pt-BR')} às ${dataValida.toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    })}`;
  }

  fmtDataCurta(data: string | null | undefined): string {
    const dataValida = this.criarData(data);
    if (!dataValida) return '-';
    return dataValida.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' });
  }

  iniciais(nome: string): string {
    return nome
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte.charAt(0).toUpperCase())
      .join('');
  }

  pagamentoLabel(formaPagamento: FormaPagamentoVenda | null | undefined): string {
    if (formaPagamento === 'MISTO') return 'Pagamento misto';
    return this.formasPagamento.find((forma) => forma.value === formaPagamento)?.label || 'Não informado';
  }

  pagamentoVendaLabel(venda: VendaResponse | null | undefined): string {
    if (!venda) return 'Não informado';
    return (venda.pagamentos?.length || 0) > 1
      ? 'Pagamento misto'
      : this.pagamentoLabel(venda.pagamentos?.[0]?.formaPagamento || venda.formaPagamento);
  }

  taxaPagamentoDaVenda(venda: VendaResponse): number {
    return Number(venda.taxaPagamentoValor || 0);
  }

  valorLiquidoDaVenda(venda: VendaResponse): number {
    const valorLiquido = Number(venda.valorLiquido);
    return Number.isFinite(valorLiquido) ? valorLiquido : Number(venda.total || 0);
  }

  private criarData(data: string | null | undefined): Date | null {
    if (!data) return null;
    const dataValida = new Date(data);
    return Number.isNaN(dataValida.getTime()) ? null : dataValida;
  }

  private enviarNovaVenda(payload: VendaRequest): void {
    const chave = this.idempotencyKey || this.criarIdempotencyKey();
    this.idempotencyKey = chave;

    this.apiService.registrarVenda(payload, chave).subscribe({
      next: (venda) => {
        this.mostrarSucesso(
          `Venda #${venda.id} registrada. Total: R$ ${this.fmt(venda.total)} · Líquido: R$ ${this.fmt(this.valorLiquidoDaVenda(venda))}`,
        );
        this.resetarFormulario(true);
        this.carregarProdutos();
        this.carregarVendas();
      },
      error: (error) => {
        this.loading = false;
        const status = Number(error.status);
        this.checkoutIncerto =
          status === 0 ||
          status === 408 ||
          status === 409 ||
          status === 425 ||
          status === 429 ||
          status >= 500;
        const mensagemPadrao = this.checkoutIncerto
          ? 'Não foi possível confirmar o resultado. Tente novamente com segurança ou descarte o checkout.'
          : 'Erro ao registrar venda.';
        this.mostrarErro(
          error.error?.message || (this.checkoutIncerto ? mensagemPadrao : error.message) || mensagemPadrao,
        );
      },
    });
  }

  private substituirVendaNoHistorico(vendaAtualizada: VendaResponse): void {
    const indice = this.vendas.findIndex((venda) => venda.id === vendaAtualizada.id);
    if (indice < 0) {
      this.vendas = [vendaAtualizada, ...this.vendas];
      return;
    }
    this.vendas = this.vendas.map((venda, index) =>
      index === indice ? vendaAtualizada : venda,
    );
  }

  private criarIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (caractere) => {
      const aleatorio = Math.floor(Math.random() * 16);
      const valor = caractere === 'x' ? aleatorio : (aleatorio & 0x3) | 0x8;
      return valor.toString(16);
    });
  }

  private resetarFormulario(renovarIdempotencia = false): void {
    this.carrinho = [];
    this.observacao = '';
    this.formaPagamento = 'PIX';
    this.taxaPagamentoPercentual = 0;
    this.pagamentoDividido = false;
    this.pagamentosCheckout = [this.novoPagamento('PIX', 0)];
    this.vendaEmEdicaoId = null;
    this.produtoSelecionadoId = null;
    this.quantidade = 1;
    this.buscaProduto = '';
    this.quantidadeOriginalPorProduto.clear();
    this.loading = false;
    this.checkoutIncerto = false;
    this.payloadPostPendente = null;
    if (renovarIdempotencia) this.idempotencyKey = null;
  }

  private mostrarSucesso(mensagem: string): void {
    this.limparFeedbackTimer();
    this.erro = '';
    this.sucesso = mensagem;
    this.feedbackTimer = setTimeout(() => (this.sucesso = ''), 6000);
  }

  private mostrarErro(mensagem: string): void {
    this.limparFeedbackTimer();
    this.sucesso = '';
    this.erro = mensagem;
    this.feedbackTimer = setTimeout(() => (this.erro = ''), 6000);
  }

  private limparErro(): void {
    if (!this.erro) return;
    this.erro = '';
    this.limparFeedbackTimer();
  }

  private limparFeedbackTimer(): void {
    if (this.feedbackTimer) clearTimeout(this.feedbackTimer);
  }

  private normalizar(valor: string): string {
    return valor
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }

  private novoPagamento(formaPagamento: FormaPagamento, valor: number): CheckoutPagamento {
    return {
      localId: this.proximoPagamentoId++,
      formaPagamento,
      valor: this.arredondarMoeda(valor),
      adquirenteId: null,
      bandeira: null,
      parcelas: 1,
      valorRecebido: formaPagamento === 'DINHEIRO' ? this.arredondarMoeda(valor) : null,
    };
  }

  private podeAlterarVenda(): boolean {
    if (this.tenantService.podeGerenciar()) return true;
    this.mostrarErro('Seu perfil permite registrar e consultar vendas, mas não editar ou cancelar.');
    return false;
  }

  private configuracaoPagamento(pagamento: CheckoutPagamento): ConfiguracaoTaxa | undefined {
    return selectPaymentRule(this.configuracoesTaxa, pagamento);
  }

  private pagamentosParaEnvio(): PagamentoVendaRequest[] {
    return this.pagamentosCheckout.map((item) => ({
      formaPagamento: item.formaPagamento,
      valor: this.arredondarMoeda(this.valorEfetivoPagamento(item)),
      adquirenteId: item.adquirenteId ?? null,
      bandeira: item.bandeira?.trim().toUpperCase() || null,
      parcelas: Number(item.parcelas || 1),
      valorRecebido: item.formaPagamento === 'DINHEIRO'
        ? (item.valorRecebido == null ? null : this.arredondarMoeda(Number(item.valorRecebido)))
        : null,
    }));
  }

  private sincronizarPagamentoPrincipal(): void {
    const principal = this.pagamentosCheckout[0];
    if (!principal) return;
    this.formaPagamento = principal.formaPagamento;
    this.taxaPagamentoPercentual = Math.min(5, this.taxaPercentualPagamento(principal));
  }

  private arredondarMoeda(valor: number): number {
    return roundMoney(valor);
  }
}
