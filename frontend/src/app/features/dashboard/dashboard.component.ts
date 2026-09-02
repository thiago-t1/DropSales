import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { Produto, TopProduto, VendaRecente } from '../../core/models/api.models';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, ChartData, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  loading = true;
  erroCarregamento = false;
  atualizadoEm: Date | null = null;

  // Indicadores financeiros
  receitas = 0;
  despesas = 0;
  lucroBruto = 0;
  cmv = 0;
  taxasPagamento = 0;
  recebidoLiquido = 0;
  aReceber = 0;

  estoqueBaixo: Produto[] = [];
  topProdutos: TopProduto[] = [];
  vendasRecentes: VendaRecente[] = [];

  // Desempenho dos últimos 30 dias
  barChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        label: 'Receitas',
        data: [],
        backgroundColor: 'rgba(79, 70, 229, 0.88)',
        borderColor: '#4f46e5',
        borderWidth: 0,
        borderRadius: 7,
        borderSkipped: false,
        maxBarThickness: 28,
        categoryPercentage: 0.72,
        barPercentage: 0.78,
        hoverBackgroundColor: '#4338ca',
      },
      {
        label: 'CMV',
        data: [],
        backgroundColor: 'rgba(15, 159, 143, 0.78)',
        borderColor: '#0f9f8f',
        borderWidth: 0,
        borderRadius: 7,
        borderSkipped: false,
        maxBarThickness: 28,
        categoryPercentage: 0.72,
        barPercentage: 0.78,
        hoverBackgroundColor: '#0d8276',
      },
    ],
  };

  barChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
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
        },
      },
      y: {
        beginAtZero: true,
        border: { display: false },
        grid: { color: 'rgba(148, 163, 184, 0.16)' },
        ticks: {
          color: '#94a3b8',
          font: { size: 10 },
          padding: 8,
          callback: (valor) => `R$ ${this.fmtCompacto(Number(valor))}`,
        },
      },
    },
  };

  // Distribuição de receitas e despesas
  doughnutData: ChartData<'doughnut'> = {
    labels: ['Receitas', 'Despesas'],
    datasets: [
      {
        data: [0, 0],
        backgroundColor: ['#4f46e5', '#f43f5e'],
        hoverBackgroundColor: ['#4338ca', '#e11d48'],
        borderColor: ['transparent', 'transparent'],
        borderWidth: 0,
        hoverOffset: 5,
        borderRadius: 5,
        spacing: 2,
      },
    ],
  };

  doughnutOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '78%',
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.94)',
        padding: 12,
        cornerRadius: 10,
        callbacks: {
          label: (context) =>
            ` R$ ${(context.parsed || 0).toLocaleString('pt-BR', {
              minimumFractionDigits: 2,
            })}`,
        },
      },
    },
  };

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

  carregarDashboard(): void {
    this.loading = true;
    this.erroCarregamento = false;

    this.apiService.getDashboard().subscribe({
      next: (data) => {
        this.receitas = data.receitas;
        this.despesas = data.despesas;
        this.cmv = data.cmv ?? 0;
        this.lucroBruto = data.lucroBruto ?? (this.receitas - this.cmv);
        this.taxasPagamento = data.taxasPagamento ?? 0;
        this.recebidoLiquido = data.recebidoLiquido ?? data.saldo ?? 0;
        this.aReceber = data.aReceber ?? data.areceber ?? 0;
        this.estoqueBaixo = data.estoqueBaixo ?? [];
        this.topProdutos = data.topProdutos ?? [];
        this.vendasRecentes = data.vendasRecentes ?? [];

        const vendasDiarias = data.vendasDiarias ?? [];
        const custosDiarios =
          data.custosDiarios ?? vendasDiarias.map((venda) => ({ data: venda.data, total: 0 }));

        this.barChartData = {
          ...this.barChartData,
          labels: vendasDiarias.map((venda) => this.fmtData(venda.data)),
          datasets: [
            {
              ...this.barChartData.datasets[0],
              data: vendasDiarias.map((venda) => venda.total),
            },
            {
              ...this.barChartData.datasets[1],
              data: custosDiarios.map((custo) => custo.total),
            },
          ],
        };

        this.doughnutData = {
          ...this.doughnutData,
          datasets: [
            {
              ...this.doughnutData.datasets[0],
              data: [this.receitas, this.despesas],
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
      },
      error: () => {
        this.loading = false;
        this.erroCarregamento = true;
        this.apiService
          .getProdutosEstoqueBaixo()
          .subscribe({ next: (produtos) => (this.estoqueBaixo = produtos) });
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
    if (!valor) return '—';
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

  get percentualCmv(): number {
    return this.receitas > 0 ? (this.cmv / this.receitas) * 100 : 0;
  }

  get percentualDespesas(): number {
    return this.receitas > 0 ? (this.despesas / this.receitas) * 100 : 0;
  }

  get totalMovimentado(): number {
    return this.receitas + this.despesas;
  }

  get temDadosGrafico(): boolean {
    return this.barChartData.datasets.some((dataset) =>
      dataset.data.some((valor) => Number(valor) > 0),
    );
  }

  get temMovimentacaoFinanceira(): boolean {
    return this.receitas > 0 || this.despesas > 0;
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
