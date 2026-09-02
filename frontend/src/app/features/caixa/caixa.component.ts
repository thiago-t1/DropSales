import { CommonModule } from '@angular/common';
import { Component, DestroyRef, effect, HostListener, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { FormaPagamento } from '../../core/models/api.models';
import { Recebivel, StatusRecebivel } from '../../core/models/business.models';
import { BusinessApiService } from '../../core/services/business-api.service';
import { TenantService } from '../../core/services/tenant.service';
import {
  agruparPorForma,
  calcularResumoFechamento,
  CaixaSecao,
  dataNoFuso,
  normalizarSecaoCaixa,
  ResumoFechamento,
  TotalPorForma,
} from './caixa.utils';

type FiltroPagamento = 'TODAS' | FormaPagamento;

@Component({
  selector: 'app-caixa',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './caixa.component.html',
})
export class CaixaComponent implements OnInit {
  recebiveis: Recebivel[] = [];
  pendente = 0;
  recebido = 0;
  abaAtiva: CaixaSecao = 'visao-geral';
  filtroStatus: 'TODOS' | StatusRecebivel = 'PENDENTE';
  filtroPagamento: FiltroPagamento = 'TODAS';
  busca = '';
  dataFechamento = '';
  loading = true;
  processandoId: number | null = null;
  erro = '';
  sucesso = '';
  private dataFechamentoAlterada = false;

  readonly abas: Array<{ id: CaixaSecao; label: string; descricao: string }> = [
    { id: 'visao-geral', label: 'Visão geral', descricao: 'Resumo do caixa' },
    { id: 'a-receber', label: 'A receber', descricao: 'Agenda de entradas' },
    { id: 'movimentacoes', label: 'Movimentações', descricao: 'Entradas confirmadas' },
    { id: 'fechamento', label: 'Fechamento', descricao: 'Conferência diária' },
  ];

  readonly statusOptions: Array<{ value: 'TODOS' | StatusRecebivel; label: string }> = [
    { value: 'TODOS', label: 'Todos os status' },
    { value: 'PENDENTE', label: 'Pendentes' },
    { value: 'RECEBIDO', label: 'Recebidos' },
    { value: 'CANCELADO', label: 'Cancelados' },
  ];

  readonly pagamentoOptions: Array<{ value: FiltroPagamento; label: string }> = [
    { value: 'TODAS', label: 'Todas as formas' },
    { value: 'DINHEIRO', label: 'Dinheiro' },
    { value: 'PIX', label: 'Pix' },
    { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
    { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
  ];

  constructor(
    private readonly api: BusinessApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly destroyRef: DestroyRef,
    readonly tenant: TenantService,
  ) {
    effect(() => {
      const timezoneDaLoja = this.tenant.lojaAtual()?.timezone;
      if (timezoneDaLoja && !this.dataFechamentoAlterada) {
        this.dataFechamento = dataNoFuso(new Date(), timezoneDaLoja);
      }
    });
  }

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((parametros) => {
        this.abaAtiva = normalizarSecaoCaixa(parametros.get('secao'));
        this.manterAbaVisivel();
      });
    this.dataFechamento = dataNoFuso(new Date(), this.timezone);
    this.carregar();
  }

  carregar(): void {
    this.loading = true;
    this.erro = '';
    this.api.listarRecebiveis().subscribe({
      next: (resumo) => {
        this.recebiveis = resumo.recebiveis;
        this.pendente = Number(resumo.pendente || 0);
        this.recebido = Number(resumo.recebido || 0);
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.erro = error.error?.message || 'Não foi possível carregar os dados do caixa.';
      },
    });
  }

  selecionarAba(aba: CaixaSecao): void {
    this.abaAtiva = aba;
    this.manterAbaVisivel();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { secao: aba === 'visao-geral' ? null : aba },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    this.manterAbaVisivel();
  }

  navegarAbas(evento: KeyboardEvent, abaAtual: CaixaSecao): void {
    const indiceAtual = this.abas.findIndex((aba) => aba.id === abaAtual);
    let proximoIndice: number | null = null;
    if (evento.key === 'ArrowRight') proximoIndice = (indiceAtual + 1) % this.abas.length;
    if (evento.key === 'ArrowLeft') proximoIndice = (indiceAtual - 1 + this.abas.length) % this.abas.length;
    if (evento.key === 'Home') proximoIndice = 0;
    if (evento.key === 'End') proximoIndice = this.abas.length - 1;
    if (proximoIndice === null) return;

    evento.preventDefault();
    const proximaAba = this.abas[proximoIndice].id;
    this.selecionarAba(proximaAba);
    window.setTimeout(() => document.getElementById(`caixa-tab-${proximaAba}`)?.focus());
  }

  alterarDataFechamento(data: string): void {
    this.dataFechamentoAlterada = true;
    this.dataFechamento = data;
  }

  get timezone(): string {
    return this.tenant.lojaAtual()?.timezone || 'America/Sao_Paulo';
  }

  get agendaFiltrada(): Recebivel[] {
    const termo = this.busca.trim().toLocaleLowerCase('pt-BR');
    return this.recebiveis.filter((item) => {
      const statusOk = this.filtroStatus === 'TODOS' || item.status === this.filtroStatus;
      return statusOk && this.correspondeBusca(item, termo);
    });
  }

  get movimentacoesFiltradas(): Recebivel[] {
    const termo = this.busca.trim().toLocaleLowerCase('pt-BR');
    return this.recebiveis
      .filter((item) =>
        item.status === 'RECEBIDO'
        && (this.filtroPagamento === 'TODAS' || item.formaPagamento === this.filtroPagamento)
        && this.correspondeBusca(item, termo),
      )
      .sort((a, b) => (b.recebidoEm || '').localeCompare(a.recebidoEm || ''));
  }

  get proximasEntradas(): Recebivel[] {
    const hoje = dataNoFuso(new Date(), this.timezone);
    return this.recebiveis
      .filter((item) => item.status === 'PENDENTE' && item.dataPrevista >= hoje)
      .sort((a, b) => a.dataPrevista.localeCompare(b.dataPrevista))
      .slice(0, 4);
  }

  get vencidos(): number {
    const hoje = dataNoFuso(new Date(), this.timezone);
    return this.recebiveis.filter((item) =>
      item.status === 'PENDENTE' && item.dataPrevista < hoje,
    ).length;
  }

  get pendentesQuantidade(): number {
    return this.recebiveis.filter((item) => item.status === 'PENDENTE').length;
  }

  get recebidosQuantidade(): number {
    return this.recebiveis.filter((item) => item.status === 'RECEBIDO').length;
  }

  get proximaEntrada(): Recebivel | null {
    return this.proximasEntradas[0] ?? null;
  }

  get percentualRecebido(): number {
    const total = this.pendente + this.recebido;
    return total > 0 ? Math.round((this.recebido / total) * 100) : 0;
  }

  get proximosSeteDias(): number {
    const agora = new Date();
    const hoje = dataNoFuso(agora, this.timezone);
    const limiteData = new Date(agora);
    limiteData.setDate(limiteData.getDate() + 7);
    const limite = dataNoFuso(limiteData, this.timezone);
    return this.recebiveis
      .filter((item) =>
        item.status === 'PENDENTE'
        && item.dataPrevista >= hoje
        && item.dataPrevista <= limite,
      )
      .reduce((total, item) => total + Number(item.valorLiquido), 0);
  }

  get taxasConfirmadas(): number {
    return this.recebiveis
      .filter((item) => item.status === 'RECEBIDO')
      .reduce((total, item) => total + Number(item.taxaValor), 0);
  }

  get totaisPorForma(): TotalPorForma[] {
    return agruparPorForma(this.recebiveis.filter((item) => item.status === 'RECEBIDO'));
  }

  get resumoFechamento(): ResumoFechamento {
    return calcularResumoFechamento(
      this.recebiveis,
      this.dataFechamento,
      this.timezone,
    );
  }

  get hoje(): string {
    return dataNoFuso(new Date(), this.timezone);
  }

  confirmar(item: Recebivel): void {
    if (item.status !== 'PENDENTE' || this.processandoId !== null) return;
    if (!window.confirm(`Confirmar a entrada de R$ ${this.fmt(item.valorLiquido)} no caixa?`)) return;
    this.processandoId = item.id;
    this.erro = '';
    this.api.confirmarRecebimento(item.id).subscribe({
      next: () => {
        this.processandoId = null;
        this.sucesso = `Entrada da venda #${item.vendaId} confirmada no caixa.`;
        this.carregar();
        window.setTimeout(() => (this.sucesso = ''), 5000);
      },
      error: (error) => {
        this.processandoId = null;
        this.erro = error.error?.message || 'Não foi possível confirmar esta entrada.';
      },
    });
  }

  pagamentoLabel(forma: FormaPagamento): string {
    const labels: Record<FormaPagamento, string> = {
      DINHEIRO: 'Dinheiro',
      PIX: 'Pix',
      CARTAO_DEBITO: 'Cartão de débito',
      CARTAO_CREDITO: 'Cartão de crédito',
    };
    return labels[forma] || forma;
  }

  statusLabel(status: StatusRecebivel): string {
    return { PENDENTE: 'Pendente', RECEBIDO: 'Recebido', CANCELADO: 'Cancelado' }[status];
  }

  percentualForma(total: number): number {
    return this.recebido > 0 ? Math.round((total / this.recebido) * 100) : 0;
  }

  fmt(valor: number): string {
    return Number(valor || 0).toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  fmtData(data: string): string {
    if (!data) return 'data selecionada';
    const valor = new Date(`${data}T12:00:00`);
    return Number.isNaN(valor.getTime()) ? 'data selecionada' : valor.toLocaleDateString('pt-BR');
  }

  fmtDataHora(data: string | null): string {
    if (!data) return 'Horário não informado';
    const valor = new Date(data);
    if (Number.isNaN(valor.getTime())) return 'Horário não informado';
    const opcoes: Intl.DateTimeFormatOptions = {
      timeZone: this.timezone,
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    };
    try {
      return valor.toLocaleString('pt-BR', opcoes);
    } catch {
      return valor.toLocaleString('pt-BR', {
        ...opcoes,
        timeZone: 'America/Sao_Paulo',
      });
    }
  }

  private correspondeBusca(item: Recebivel, termo: string): boolean {
    return !termo
      || String(item.vendaId).includes(termo)
      || (item.adquirente || '').toLocaleLowerCase('pt-BR').includes(termo)
      || this.pagamentoLabel(item.formaPagamento).toLocaleLowerCase('pt-BR').includes(termo);
  }

  private manterAbaVisivel(): void {
    window.setTimeout(() => {
      document.getElementById(`caixa-tab-${this.abaAtiva}`)?.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
        inline: 'center',
      });
    });
  }
}
