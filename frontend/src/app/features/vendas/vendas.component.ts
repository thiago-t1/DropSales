import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Produto, ItemVendaRequest, VendaResponse } from '../../core/models/api.models';

interface CartItem {
  produto: Produto;
  quantidade: number;
  subtotal: number;
}

@Component({
  selector: 'app-vendas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './vendas.component.html',
})
export class VendasComponent implements OnInit {
  // ─── Listas ───────────────────────────────────────────────────────────────
  produtos: Produto[] = [];
  vendas: VendaResponse[] = [];

  // ─── Formulário de nova / edição de venda ─────────────────────────────────
  carrinho: CartItem[] = [];
  produtoSelecionadoId: number | null = null;
  quantidade = 1;
  observacao = '';

  /** ID da venda em edição (null = nova venda) */
  vendaEmEdicaoId: number | null = null;

  // ─── Estados de UI ────────────────────────────────────────────────────────
  loading = false;
  loadingVendas = true;
  sucesso = '';
  erro = '';

  // ─── Modal de detalhes de itens ───────────────────────────────────────────
  showDetalheModal = false;
  vendaDetalhe: VendaResponse | null = null;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.carregarProdutos();
    this.carregarVendas();
  }

  // ── Carregamento ────────────────────────────────────────────────────────────

  carregarProdutos(): void {
    this.apiService.getProdutos().subscribe({
      next: (p) => (this.produtos = p),
      error: () => {},
    });
  }

  carregarVendas(): void {
    this.loadingVendas = true;
    this.apiService.getVendas().subscribe({
      next: (v) => {
        this.vendas = v;
        this.loadingVendas = false;
      },
      error: (e) => {
        this.loadingVendas = false;
        console.error('Erro ao carregar vendas:', e);
      },
    });
  }

  // ── Formulário de venda ─────────────────────────────────────────────────────

  get produtoSelecionado(): Produto | undefined {
    return this.produtos.find((p) => p.id === this.produtoSelecionadoId);
  }

  get totalCarrinho(): number {
    return this.carrinho.reduce((s, i) => s + i.subtotal, 0);
  }

  get modoEdicao(): boolean {
    return this.vendaEmEdicaoId !== null;
  }

  adicionarItem(): void {
    if (!this.produtoSelecionadoId || this.quantidade < 1) return;
    const prod = this.produtoSelecionado;
    if (!prod) return;
    const ex = this.carrinho.find((c) => c.produto.id === prod.id);
    if (ex) {
      ex.quantidade += this.quantidade;
      ex.subtotal = ex.quantidade * ex.produto.precoVenda;
    } else {
      this.carrinho.push({
        produto: prod,
        quantidade: this.quantidade,
        subtotal: this.quantidade * prod.precoVenda,
      });
    }
    this.produtoSelecionadoId = null;
    this.quantidade = 1;
  }

  removerItem(i: number): void {
    this.carrinho.splice(i, 1);
  }

  /**
   * Submete o formulário:
   * - Se vendaEmEdicaoId === null → POST (nova venda)
   * - Se vendaEmEdicaoId !== null → PUT (editar venda existente)
   */
  finalizarVenda(): void {
    if (!this.carrinho.length) return;
    this.loading = true;
    this.sucesso = '';
    this.erro = '';

    const itens: ItemVendaRequest[] = this.carrinho.map((c) => ({
      produtoId: c.produto.id,
      quantidade: c.quantidade,
    }));
    const payload = { itens, observacao: this.observacao };

    if (this.vendaEmEdicaoId === null) {
      // ── Nova venda ──
      this.apiService.registrarVenda(payload).subscribe({
        next: (r) => {
          this.sucesso = `Venda #${r.id} registrada com sucesso! Total: R$ ${this.fmt(r.total)}`;
          this.resetarFormulario();
          this.carregarProdutos();
          this.carregarVendas();
          setTimeout(() => (this.sucesso = ''), 6000);
        },
        error: (e) => {
          this.erro = e.error?.message || e.message || 'Erro ao registrar venda.';
          this.loading = false;
          console.error('Erro ao registrar venda:', e);
          setTimeout(() => (this.erro = ''), 6000);
        },
      });
    } else {
      // ── Editar venda existente ──
      const id = this.vendaEmEdicaoId;
      this.apiService.editarVenda(id, payload).subscribe({
        next: (r) => {
          this.sucesso = `Venda #${r.id} atualizada com sucesso! Total: R$ ${this.fmt(r.total)}`;
          this.resetarFormulario();
          this.carregarProdutos();
          this.carregarVendas();
          setTimeout(() => (this.sucesso = ''), 6000);
        },
        error: (e) => {
          this.erro = e.error?.message || e.message || 'Erro ao editar venda.';
          this.loading = false;
          console.error('Erro ao editar venda:', e);
          setTimeout(() => (this.erro = ''), 6000);
        },
      });
    }
  }

  /**
   * Preenche o formulário com os itens de uma venda existente para edição inline.
   */
  carregarParaEdicao(venda: VendaResponse): void {
    this.vendaEmEdicaoId = venda.id;
    this.observacao = venda.observacao || '';
    this.carrinho = [];

    for (const item of venda.itens) {
      const produto = this.produtos.find((p) => p.id === item.produtoId);
      if (produto) {
        this.carrinho.push({
          produto,
          quantidade: item.quantidade,
          subtotal: Number(item.subtotal),
        });
      } else {
        // Produto removido do catálogo: cria stub para manter o item visível
        this.carrinho.push({
          produto: {
            id: item.produtoId,
            nome: item.produto,
            sku: '',
            precoCusto: 0,
            precoVenda: Number(item.precoUnitario),
            quantidadeEstoque: 0,
            estoqueMinimo: 0,
            categoria: '',
            estoqueBaixo: false,
          },
          quantidade: item.quantidade,
          subtotal: Number(item.subtotal),
        });
      }
    }

    // Rola até o topo do formulário suavemente
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelarEdicao(): void {
    this.resetarFormulario();
  }

  private resetarFormulario(): void {
    this.carrinho = [];
    this.observacao = '';
    this.vendaEmEdicaoId = null;
    this.produtoSelecionadoId = null;
    this.quantidade = 1;
    this.loading = false;
  }

  // ── Exclusão ────────────────────────────────────────────────────────────────

  excluirVenda(id: number): void {
    if (!confirm(`Deseja estornar a Venda #${id}? O estoque será devolvido e as movimentações financeiras removidas.`)) return;
    this.apiService.cancelarVenda(id).subscribe({
      next: () => {
        this.sucesso = `Venda #${id} estornada com sucesso!`;
        // Se estava editando esta venda, resetar formulário
        if (this.vendaEmEdicaoId === id) this.resetarFormulario();
        this.carregarProdutos();
        this.carregarVendas();
        setTimeout(() => (this.sucesso = ''), 6000);
      },
      error: (e) => {
        this.erro = e.error?.message || e.message || 'Erro ao estornar venda.';
        console.error('Erro ao excluir venda:', e);
        setTimeout(() => (this.erro = ''), 6000);
      },
    });
  }

  // ── Modal de detalhes ────────────────────────────────────────────────────────

  verDetalhes(venda: VendaResponse): void {
    this.vendaDetalhe = venda;
    this.showDetalheModal = true;
  }

  fecharDetalhes(): void {
    this.showDetalheModal = false;
    this.vendaDetalhe = null;
  }

  // ── Utilitários ─────────────────────────────────────────────────────────────

  fmt(v: number | string): string {
    const n = typeof v === 'string' ? parseFloat(v) : v;
    if (isNaN(n)) return '0,00';
    return n.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
  }

  fmtData(d: string | null | undefined): string {
    if (!d) return '-';
    // Sem timezone → adiciona 'Z' para forçar UTC e evitar desvio de fuso
    const iso = d.includes('Z') || d.includes('+') ? d : d + 'Z';
    const dt = new Date(iso);
    if (isNaN(dt.getTime())) return String(d);
    return (
      dt.toLocaleDateString('pt-BR') +
      ' ' +
      dt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    );
  }
}