import { Component, OnInit, ViewChildren, QueryList } from '@angular/core';
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

  // KPI cards
  saldo        = 0;
  receitas     = 0;
  despesas     = 0;
  lucroLiquido = 0;
  cmv          = 0;

  estoqueBaixo:  Produto[]      = [];
  topProdutos:   TopProduto[]   = [];
  vendasRecentes: VendaRecente[] = [];

  // ---- Gráfico de barras 30 dias (Receitas + CMV) ----
  barChartData: ChartData<'bar'> = {
    labels: [],
    datasets: [
      {
        label: 'Receitas',
        data: [],
        backgroundColor: 'rgba(99, 102, 241, 0.9)',
        borderColor: 'rgba(99, 102, 241, 1)',
        borderWidth: 0, borderRadius: 6, borderSkipped: false,
        hoverBackgroundColor: 'rgba(99, 102, 241, 1)',
      },
      {
        label: 'Custo',
        data: [],
        backgroundColor: 'rgba(244, 63, 94, 0.9)',
        borderColor: 'rgba(244, 63, 94, 1)',
        borderWidth: 0, borderRadius: 6, borderSkipped: false,
        hoverBackgroundColor: 'rgba(244, 63, 94, 1)',
      },
    ],
  };

  barChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true, position: 'top',
        labels: { color: '#64748b', usePointStyle: true, pointStyleWidth: 10, font: { size: 12 } },
      },
      tooltip: {
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        padding: 12, cornerRadius: 8,
        callbacks: {
          label: (ctx) => ` R$ ${(ctx.parsed.y || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2 })}`,
        },
      },
    },
    scales: {
      x: { grid: { display: false }, ticks: { color: '#94a3b8', font: { size: 10 }, maxRotation: 0 } },
      y: {
        beginAtZero: true,
        grid: { color: 'rgba(0,0,0,0.03)' },
        ticks: { color: '#94a3b8', font: { size: 11 }, callback: (v) => 'R$ ' + Number(v).toLocaleString('pt-BR') },
      },
    },
  };

  // ---- Doughnut: Resumo de Transações ----
  doughnutData: ChartData<'doughnut'> = {
    labels: ['Receitas', 'Despesas'],
    datasets: [{
      data: [0, 0],
      backgroundColor: ['rgba(99, 102, 241, 0.9)', 'rgba(244, 63, 94, 0.9)'],
      borderColor: ['transparent', 'transparent'],
      borderWidth: 0, hoverOffset: 8, borderRadius: 4,
    }],
  };

  doughnutOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true, maintainAspectRatio: false, cutout: '80%',
    plugins: {
      legend: {
        position: 'right',
        labels: { color: '#64748b', usePointStyle: true, pointStyleWidth: 10, font: { size: 12 }, padding: 16 },
      },
      tooltip: {
        backgroundColor: 'rgba(15,23,42,0.92)', padding: 12, cornerRadius: 8,
        callbacks: {
          label: (ctx) => ` R$ ${(ctx.parsed || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2 })}`,
        },
      },
    },
  };

  // ---- Bar horizontal: Top 5 Produtos ----
  topBarData: ChartData<'bar'> = {
    labels: [],
    datasets: [{
      label: 'Unidades Vendidas',
      data: [],
      backgroundColor: 'rgba(139, 92, 246, 0.9)',
      borderColor: 'rgba(139, 92, 246, 1)',
      borderWidth: 0, borderRadius: 6, borderSkipped: false,
    }],
  };

  topBarOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true, maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(15,23,42,0.92)', padding: 10, cornerRadius: 8,
        callbacks: { label: (ctx) => ` ${ctx.parsed.x} un.` },
      },
    },
    scales: {
      x: { beginAtZero: true, grid: { color: 'rgba(0,0,0,0.03)' }, ticks: { color: '#94a3b8', font: { size: 11 } } },
      y: { grid: { display: false }, ticks: { color: '#475569', font: { size: 12 } } },
    },
  };

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.apiService.getDashboard().subscribe({
      next: (data) => {
        this.saldo        = data.saldo;
        this.receitas     = data.receitas;
        this.despesas     = data.despesas;
        this.lucroLiquido = data.lucroLiquido;
        this.cmv          = data.cmv ?? 0;
        this.estoqueBaixo  = data.estoqueBaixo   ?? [];
        this.topProdutos   = data.topProdutos    ?? [];
        this.vendasRecentes = data.vendasRecentes ?? [];

        // Bar chart (30 dias)
        if (data.vendasDiarias?.length) {
          this.barChartData = {
            ...this.barChartData,
            labels: data.vendasDiarias.map(v => v.data),
            datasets: [
              { ...this.barChartData.datasets[0], data: data.vendasDiarias.map(v => v.total) },
              { ...this.barChartData.datasets[1], data: (data.custosDiarios ?? data.vendasDiarias.map(() => 0)).map(v => v.total) },
            ],
          };
        }

        // Doughnut
        this.doughnutData = {
          ...this.doughnutData,
          datasets: [{ ...this.doughnutData.datasets[0], data: [this.receitas, this.despesas] }],
        };

        // Top 5 produtos
        if (this.topProdutos.length) {
          this.topBarData = {
            ...this.topBarData,
            labels: this.topProdutos.map(p => p.nome),
            datasets: [{ ...this.topBarData.datasets[0], data: this.topProdutos.map(p => p.totalUnidades) }],
          };
        }

        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.apiService.getProdutosEstoqueBaixo().subscribe({ next: (p) => (this.estoqueBaixo = p) });
      },
    });
  }

  fmt(v: number): string { return (v ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 2 }); }

  estoquePercent(p: Produto): number {
    if (!p.estoqueMinimo || p.estoqueMinimo === 0) return 100;
    const ratio = p.quantidadeEstoque / (p.estoqueMinimo * 2);
    return Math.min(100, Math.max(0, Math.round(ratio * 100)));
  }

  estoqueStatus(p: Produto): 'critico' | 'baixo' | 'ok' {
    if (p.quantidadeEstoque === 0) return 'critico';
    if (p.quantidadeEstoque <= p.estoqueMinimo) return 'baixo';
    return 'ok';
  }
}