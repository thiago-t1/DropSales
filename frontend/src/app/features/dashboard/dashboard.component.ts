import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { Produto, TopProduto, VendaRecente } from '../../core/models/api.models';
import { BaseChartDirective } from 'ng2-charts';
import {
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  ChartData,
  Legend,
  LinearScale,
  Tooltip,
} from 'chart.js';
import { formatarRotuloData, prepararSerieAtiva } from './dashboard-chart.utils';

Chart.register(
  BarController,
  BarElement,
  CategoryScale,
  Legend,
  LinearScale,
  Tooltip,
);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  loading = true;
  refreshing = false;
  erroCarregamento = false;
  atualizadoEm: Date | null = null;

  // Indicadores financeiros
  receitas = 0;
  lucroBruto = 0;
  cmv = 0;
  taxasPagamento = 0;
  lucroLiquido = 0;

  estoqueBaixo: Produto[] = [];
  topProdutos: TopProduto[] = [];
  vendasRecentes: VendaRecente[] = [];

  chartPeriodLabel = '7 dias';
  receitaPeriodo = 0;
  cmvPeriodo = 0;

  // Janela adaptativa: preserva contexto sem exibir semanas vazias.
  barChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        label: 'Faturamento',
        data: [],
        backgroundColor: '#0e7490',
        hoverBackgroundColor: '#155e75',
        borderRadius: 7,
        borderSkipped: false,
        maxBarThickness: 28,
      },
      {
        label: 'Custo dos produtos',
        data: [],
        backgroundColor: '#5eead4',
        hoverBackgroundColor: '#2dd4bf',
        borderRadius: 6,
        borderSkipped: false,
        maxBarThickness: 28,
      },
    ],
  };

  barChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 320 },
    interaction: { intersect: false, mode: 'index' },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.94)',
        titleColor: '#cbd5e1',
        bodyColor: '#ffffff',
        displayColors: true,
        boxWidth: 9,
        boxHeight: 9,
        padding: 12,
        cornerRadius: 10,
        filter: (context) => Number(context.raw) !== 0,
        callbacks: {
          label: (context) =>
            ` ${context.dataset.label}: R$ ${(context.parsed.y || 0).toLocaleString('pt-BR', {
              minimumFractionDigits: 2,
            })}`,
        },
      },
    },
    scales: {
      x: {
        border: { display: false },
        grid: { display: false },
        ticks: {
          color: '#94a3b8',
          font: { size: 10, weight: 500 },
          maxRotation: 0,
          autoSkipPadding: 18,
          maxTicksLimit: 8,
        },
      },
      y: {
        beginAtZero: true,
        grace: '12%',
        border: { display: false },
        grid: { color: 'rgba(148, 163, 184, 0.16)' },
        ticks: {
          color: '#94a3b8',
          font: { size: 10 },
          padding: 8,
          maxTicksLimit: 5,
          callback: (valor) => `R$ ${this.fmtCompacto(Number(valor))}`,
        },
      },
    },
  };

  // Distribuição de receitas e despesas
  // Ranking de produtos
  topBarData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        label: 'Unidades vendidas',
        data: [],
        backgroundColor: 'rgba(15, 159, 143, 0.82)',
        hoverBackgroundColor: '#0d8276',
        borderColor: '#0f9f8f',
        borderWidth: 0,
        borderRadius: 7,
        borderSkipped: false,
        maxBarThickness: 24,
        categoryPercentage: 0.68,
      },
    ],
  };

  topBarOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    interaction: { intersect: false, mode: 'nearest' },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.94)',
        padding: 10,
        cornerRadius: 10,
        callbacks: { label: (context) => ` ${context.parsed.x} un.` },
      },
    },
    scales: {
      x: {
        beginAtZero: true,
        border: { display: false },
        grid: { color: 'rgba(148, 163, 184, 0.16)' },
        ticks: { color: '#94a3b8', precision: 0, font: { size: 10 } },
      },
      y: {
        border: { display: false },
        grid: { display: false },
        ticks: { color: '#94a3b8', font: { size: 11, weight: 500 } },
      },
    },
  };

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.carregarDashboard();
  }

  carregarDashboard(force = false): void {
    const primeiraCarga = this.atualizadoEm === null;
    this.loading = primeiraCarga;
    this.refreshing = !primeiraCarga;
    this.erroCarregamento = false;

    this.apiService.getDashboard(force).subscribe({
      next: (data) => {
        this.receitas = data.receitas;
        this.cmv = data.cmv ?? 0;
        this.lucroBruto = data.lucroBruto ?? (this.receitas - this.cmv);
        this.taxasPagamento = data.taxasPagamento ?? 0;
        this.lucroLiquido = data.lucroLiquido ?? data.saldoOperacional ?? data.saldo ?? 0;
        this.estoqueBaixo = data.estoqueBaixo ?? [];
        this.topProdutos = data.topProdutos ?? [];
        this.vendasRecentes = data.vendasRecentes ?? [];

        const vendasDiarias = data.vendasDiarias ?? [];
        const custosDiarios = data.custosDiarios ?? [];
        const serie = prepararSerieAtiva(vendasDiarias, custosDiarios);
        this.receitaPeriodo = serie.reduce((total, ponto) => total + ponto.receita, 0);
        this.cmvPeriodo = serie.reduce((total, ponto) => total + ponto.cmv, 0);
        this.chartPeriodLabel = serie.length === 1 ? 'Hoje' : `${serie.length} dias`;

        this.barChartData = {
          ...this.barChartData,
          labels: serie.map((ponto) => this.fmtData(ponto.data)),
          datasets: [
            {
              ...this.barChartData.datasets[0],
              data: serie.map((ponto) => ponto.receita),
            },
            {
              ...this.barChartData.datasets[1],
              data: serie.map((ponto) => ponto.cmv),
            },
          ],
        };

        this.topBarData = {
          ...this.topBarData,
          labels: this.topProdutos.map((produto) => produto.nome),
          datasets: [
            {
              ...this.topBarData.datasets[0],
              data: this.topProdutos.map((produto) => produto.totalUnidades),
            },
          ],
        };

        this.atualizadoEm = new Date();
        this.loading = false;
        this.refreshing = false;
      },
      error: () => {
        this.loading = false;
        this.refreshing = false;
        this.erroCarregamento = true;
        if (primeiraCarga) {
          this.apiService
            .getProdutosEstoqueBaixo()
            .subscribe({ next: (produtos) => (this.estoqueBaixo = produtos) });
        }
      },
    });
  }

  fmt(valor: number): string {
    return (valor ?? 0).toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  fmtData(valor: string): string {
    return formatarRotuloData(valor);
  }

  fmtHora(data: Date): string {
    return new Intl.DateTimeFormat('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(data);
  }

  fmtCompacto(valor: number): string {
    return new Intl.NumberFormat('pt-BR', {
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(valor);
  }

  get margemBruta(): number {
    return this.receitas > 0 ? (this.lucroBruto / this.receitas) * 100 : 0;
  }

  get margemPeriodo(): number {
    return this.receitaPeriodo > 0
      ? ((this.receitaPeriodo - this.cmvPeriodo) / this.receitaPeriodo) * 100
      : 0;
  }

  get percentualCustoProdutos(): number {
    return this.receitas > 0 ? Math.min(100, (this.cmv / this.receitas) * 100) : 0;
  }

  get percentualTaxas(): number {
    return this.receitas > 0 ? Math.min(100, (this.taxasPagamento / this.receitas) * 100) : 0;
  }

  get percentualResultado(): number {
    return this.receitas > 0
      ? Math.max(0, Math.min(100, (this.lucroLiquido / this.receitas) * 100))
      : 0;
  }

  get temDadosGrafico(): boolean {
    return this.barChartData.datasets.some((dataset) =>
      dataset.data.some((valor) => Number(valor) > 0),
    );
  }

  get estoqueCritico(): number {
    return this.estoqueBaixo.filter((produto) => produto.quantidadeEstoque === 0).length;
  }

  get estoquePrioritario(): Produto[] {
    return [...this.estoqueBaixo]
      .sort((a, b) => a.quantidadeEstoque - b.quantidadeEstoque)
      .slice(0, 5);
  }

  iniciais(nome: string): string {
    if (!nome?.trim()) return 'DS';
    return nome
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((parte) => parte.charAt(0).toUpperCase())
      .join('');
  }

  estoquePercent(produto: Produto): number {
    if (!produto.estoqueMinimo || produto.estoqueMinimo === 0) {
      return produto.quantidadeEstoque > 0 ? 100 : 0;
    }
    const proporcao = produto.quantidadeEstoque / (produto.estoqueMinimo * 2);
    return Math.min(100, Math.max(0, Math.round(proporcao * 100)));
  }

  estoqueStatus(produto: Produto): 'critico' | 'baixo' | 'ok' {
    if (produto.quantidadeEstoque === 0) return 'critico';
    if (produto.quantidadeEstoque <= produto.estoqueMinimo) return 'baixo';
    return 'ok';
  }

}
