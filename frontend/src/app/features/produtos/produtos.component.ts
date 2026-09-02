import { Component, OnInit, ElementRef, HostListener, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { TenantService } from '../../core/services/tenant.service';
import { Produto, ProdutoRequest, ImportResultDTO } from '../../core/models/api.models';
import {
  inteiroNaoNegativo,
  produtoParaFormulario,
  produtoRequestValido,
  valorMonetarioValido,
} from './product-form.mapper';

@Component({
  selector: 'app-produtos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './produtos.component.html',
  styles: [`
    :host {
      display: block;
      color: var(--ds-text);
    }

    .product-surface {
      background: var(--ds-surface);
      border: 1px solid var(--ds-border);
      border-radius: 1.25rem;
      box-shadow: var(--ds-shadow-sm);
    }

    .product-input {
      width: 100%;
      min-height: 2.75rem;
      border: 1px solid var(--ds-border-strong);
      border-radius: 0.75rem;
      background: var(--ds-surface);
      color: var(--ds-text);
      padding: 0.625rem 0.875rem;
      font-size: 0.875rem;
      line-height: 1.25rem;
      outline: none;
      transition: border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease;
    }

    .product-input::placeholder {
      color: var(--ds-muted-soft);
    }

    .product-input:hover {
      border-color: #818cf8;
    }

    .product-input:focus {
      border-color: #6366f1;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
    }

    .product-input:disabled {
      cursor: not-allowed;
      background: var(--ds-surface-soft);
      color: var(--ds-muted);
    }

    .product-label {
      display: block;
      margin-bottom: 0.375rem;
      color: var(--ds-text-soft);
      font-size: 0.8125rem;
      font-weight: 600;
    }

    .product-modal {
      background: var(--ds-surface);
      border: 1px solid var(--ds-border);
      box-shadow: var(--ds-shadow-md);
    }

    .product-scrollbar {
      scrollbar-width: thin;
      scrollbar-color: var(--ds-border-strong) transparent;
    }

    .product-scrollbar::-webkit-scrollbar {
      width: 5px;
      height: 5px;
    }

    .product-scrollbar::-webkit-scrollbar-thumb {
      border-radius: 999px;
      background: var(--ds-border-strong);
    }

    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after {
        scroll-behavior: auto !important;
        transition-duration: 0.01ms !important;
        animation-duration: 0.01ms !important;
        animation-iteration-count: 1 !important;
      }
    }
  `],
})
export class ProdutosComponent implements OnInit {
  @ViewChild('importInput') importInput!: ElementRef<HTMLInputElement>;
  @ViewChild('produtoNomeInput') produtoNomeInput?: ElementRef<HTMLInputElement>;

  produtos: Produto[] = [];
  loading = true;
  showModal = false;
  saving = false;
  deleting = false;
  produtoParaExcluir: Produto | null = null;
  sucesso = '';
  erro = '';

  // Import Excel
  importing = false;
  importResult: ImportResultDTO | null = null;
  showImportResult = false;

  // Form fields
  editId: number | null = null;
  form: ProdutoRequest = this.emptyForm();

  // Catalog navigation
  busca = '';
  filtroEstoque: 'todos' | 'ok' | 'baixo' | 'esgotado' = 'todos';
  filtroCategoria = 'todas';
  ordenacao: 'nome' | 'estoque-asc' | 'estoque-desc' | 'preco-asc' | 'preco-desc' = 'nome';

  constructor(
    private apiService: ApiService,
    readonly tenantService: TenantService,
  ) {}

  ngOnInit(): void {
    this.carregarProdutos();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.showModal) {
      this.fecharModal();
    } else if (this.produtoParaExcluir) {
      this.cancelarExclusao();
    } else if (this.showImportResult) {
      this.fecharImportResult();
    }
  }

  carregarProdutos(): void {
    this.loading = true;
    this.apiService.getProdutos().subscribe({
      next: (p) => { this.produtos = p; this.loading = false; },
      error: () => { this.loading = false; this.erro = 'Erro ao carregar produtos.'; },
    });
  }

  emptyForm(): ProdutoRequest {
    return { nome: '', descricao: '', sku: '', precoCusto: 0, precoVenda: 0, quantidadeEstoque: 0, estoqueMinimo: 5, categoriaId: undefined };
  }

  abrirNovo(): void {
    if (!this.podeAlterarCatalogo()) return;
    this.editId = null;
    this.form = this.emptyForm();
    this.erro = '';
    this.showModal = true;
    this.focarNomeDoProduto();
  }

  abrirEditar(p: Produto): void {
    if (!this.podeAlterarCatalogo()) return;
    this.editId = p.id;
    this.form = produtoParaFormulario(p);
    this.erro = '';
    this.showModal = true;
    this.focarNomeDoProduto();
  }

  fecharModal(): void {
    this.showModal = false;
    this.erro = '';
  }

  formularioProdutoValido(): boolean {
    return produtoRequestValido(this.form);
  }

  precoFormularioValido(valor: number, minimo: number): boolean {
    return valorMonetarioValido(Number(valor), minimo);
  }

  inteiroFormularioValido(valor: number): boolean {
    return inteiroNaoNegativo(Number(valor));
  }

  salvar(formulario: NgForm): void {
    if (!this.podeAlterarCatalogo()) return;
    if (formulario.invalid || !this.formularioProdutoValido()) {
      formulario.form.markAllAsTouched();
      this.erro = 'Revise os campos destacados antes de salvar o produto.';
      return;
    }
    this.saving = true;
    this.erro = '';

    const obs = this.editId
      ? this.apiService.atualizarProduto(this.editId, this.form)
      : this.apiService.criarProduto(this.form);

    obs.subscribe({
      next: () => {
        this.sucesso = this.editId ? 'Produto atualizado!' : 'Produto cadastrado!';
        this.saving = false;
        this.showModal = false;
        this.carregarProdutos();
        setTimeout(() => (this.sucesso = ''), 4000);
      },
      error: (e) => {
        this.erro = e.error?.message || 'Erro ao salvar produto.';
        this.saving = false;
      },
    });
  }

  solicitarExclusao(produto: Produto): void {
    if (!this.podeAlterarCatalogo()) return;
    this.produtoParaExcluir = produto;
    this.erro = '';
  }

  cancelarExclusao(): void {
    if (this.deleting) return;
    this.produtoParaExcluir = null;
  }

  confirmarExclusao(): void {
    if (!this.podeAlterarCatalogo()) return;
    if (!this.produtoParaExcluir || this.deleting) return;

    this.deleting = true;
    this.apiService.excluirProduto(this.produtoParaExcluir.id).subscribe({
      next: () => {
        this.sucesso = 'Produto removido!';
        this.deleting = false;
        this.produtoParaExcluir = null;
        this.carregarProdutos();
        setTimeout(() => (this.sucesso = ''), 4000);
      },
      error: () => {
        this.deleting = false;
        this.produtoParaExcluir = null;
        this.erro = 'Erro ao excluir produto.';
        setTimeout(() => (this.erro = ''), 4000);
      },
    });
  }

  abrirImport(): void {
    if (!this.podeAlterarCatalogo()) return;
    this.importInput.nativeElement.value = '';
    this.importInput.nativeElement.click();
  }

  onImportFile(event: Event): void {
    if (!this.podeAlterarCatalogo()) return;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.importing = true;
    this.importResult = null;
    this.showImportResult = false;
    this.erro = '';

    this.apiService.importarProdutos(file).subscribe({
      next: (result) => {
        this.importResult = result;
        this.showImportResult = true;
        this.importing = false;
        this.carregarProdutos();
        const total = result.importados + result.atualizados;
        this.sucesso = `Importação concluída: ${total} produto(s) processado(s).`;
        setTimeout(() => (this.sucesso = ''), 6000);
      },
      error: (e) => {
        this.erro = e.error?.message || 'Erro ao importar planilha. Verifique o arquivo e tente novamente.';
        this.importing = false;
        setTimeout(() => (this.erro = ''), 6000);
      },
    });
  }

  fecharImportResult(): void {
    this.showImportResult = false;
    this.importResult = null;
  }

  private podeAlterarCatalogo(): boolean {
    if (this.tenantService.podeGerenciar()) return true;
    this.erro = 'Seu perfil permite consultar o catálogo, mas não alterar produtos.';
    return false;
  }

  fmt(v: number): string {
    return v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
  }

  get produtosFiltrados(): Produto[] {
    const termo = this.normalizar(this.busca.trim());

    return this.produtos
      .filter((produto) => {
        const correspondeBusca =
          !termo ||
          this.normalizar(produto.nome).includes(termo) ||
          this.normalizar(produto.sku || '').includes(termo) ||
          this.normalizar(produto.categoria || '').includes(termo);

        const correspondeCategoria =
          this.filtroCategoria === 'todas' || produto.categoria === this.filtroCategoria;

        let correspondeEstoque = true;
        if (this.filtroEstoque === 'esgotado') {
          correspondeEstoque = produto.quantidadeEstoque === 0;
        } else if (this.filtroEstoque === 'baixo') {
          correspondeEstoque = produto.quantidadeEstoque > 0 && produto.estoqueBaixo;
        } else if (this.filtroEstoque === 'ok') {
          correspondeEstoque = produto.quantidadeEstoque > 0 && !produto.estoqueBaixo;
        }

        return correspondeBusca && correspondeCategoria && correspondeEstoque;
      })
      .sort((a, b) => {
        switch (this.ordenacao) {
          case 'estoque-asc':
            return a.quantidadeEstoque - b.quantidadeEstoque;
          case 'estoque-desc':
            return b.quantidadeEstoque - a.quantidadeEstoque;
          case 'preco-asc':
            return a.precoVenda - b.precoVenda;
          case 'preco-desc':
            return b.precoVenda - a.precoVenda;
          default:
            return a.nome.localeCompare(b.nome, 'pt-BR');
        }
      });
  }

  get categorias(): string[] {
    return Array.from(
      new Set(this.produtos
        .map((produto) => produto.categoria)
        .filter((categoria): categoria is string => Boolean(categoria)))
    ).sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }

  get totalUnidades(): number {
    return this.produtos.reduce((total, produto) => total + produto.quantidadeEstoque, 0);
  }

  get produtosEstoqueBaixo(): number {
    return this.produtos.filter(
      (produto) => produto.quantidadeEstoque > 0 && produto.estoqueBaixo
    ).length;
  }

  get produtosEsgotados(): number {
    return this.produtos.filter((produto) => produto.quantidadeEstoque === 0).length;
  }

  get valorEmEstoque(): number {
    return this.produtos.reduce(
      (total, produto) => total + produto.precoCusto * produto.quantidadeEstoque,
      0
    );
  }

  get temFiltrosAtivos(): boolean {
    return Boolean(
      this.busca ||
      this.filtroEstoque !== 'todos' ||
      this.filtroCategoria !== 'todas' ||
      this.ordenacao !== 'nome'
    );
  }

  limparFiltros(): void {
    this.busca = '';
    this.filtroEstoque = 'todos';
    this.filtroCategoria = 'todas';
    this.ordenacao = 'nome';
  }

  margemProduto(produto: Produto): number {
    const precoVenda = Number(produto.precoVenda);
    const precoCusto = Number(produto.precoCusto);
    if (!Number.isFinite(precoVenda) || precoVenda <= 0 || !Number.isFinite(precoCusto)) return 0;
    if (precoCusto === 0) return 100;
    return ((precoVenda - precoCusto) / precoVenda) * 100;
  }

  private normalizar(valor: string): string {
    return valor
      .toLocaleLowerCase('pt-BR')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }

  private focarNomeDoProduto(): void {
    setTimeout(() => this.produtoNomeInput?.nativeElement.focus());
  }
}
