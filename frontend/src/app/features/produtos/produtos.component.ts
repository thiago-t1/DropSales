import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Produto, ProdutoRequest, ImportResultDTO } from '../../core/models/api.models';

@Component({
  selector: 'app-produtos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './produtos.component.html',
})
export class ProdutosComponent implements OnInit {
  @ViewChild('importInput') importInput!: ElementRef<HTMLInputElement>;

  produtos: Produto[] = [];
  loading = true;
  showModal = false;
  saving = false;
  sucesso = '';
  erro = '';

  // Import Excel
  importing = false;
  importResult: ImportResultDTO | null = null;
  showImportResult = false;

  // Form fields
  editId: number | null = null;
  form: ProdutoRequest = this.emptyForm();

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.carregarProdutos();
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
    this.editId = null;
    this.form = this.emptyForm();
    this.showModal = true;
  }

  abrirEditar(p: Produto): void {
    this.editId = p.id;
    this.form = {
      nome: p.nome,
      descricao: '',
      sku: p.sku,
      precoCusto: p.precoCusto,
      precoVenda: p.precoVenda,
      quantidadeEstoque: p.quantidadeEstoque,
      estoqueMinimo: p.estoqueMinimo,
      categoriaId: undefined,
    };
    this.showModal = true;
  }

  fecharModal(): void {
    this.showModal = false;
    this.erro = '';
  }

  salvar(): void {
    if (!this.form.nome || !this.form.precoVenda) return;
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

  excluir(id: number): void {
    if (!confirm('Deseja realmente excluir este produto?')) return;
    this.apiService.excluirProduto(id).subscribe({
      next: () => {
        this.sucesso = 'Produto removido!';
        this.carregarProdutos();
        setTimeout(() => (this.sucesso = ''), 4000);
      },
      error: () => { this.erro = 'Erro ao excluir produto.'; setTimeout(() => (this.erro = ''), 4000); },
    });
  }

  abrirImport(): void {
    this.importInput.nativeElement.value = '';
    this.importInput.nativeElement.click();
  }

  onImportFile(event: Event): void {
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
        this.sucesso = `Importacao concluida: ${total} produto(s) processado(s).`;
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

  fmt(v: number): string {
    return v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
  }
}