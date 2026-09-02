import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { FormaPagamento } from '../../core/models/api.models';
import {
  Adquirente,
  ConfiguracaoTaxa,
  ConfiguracaoTaxaPayload,
} from '../../core/models/business.models';
import { BusinessApiService } from '../../core/services/business-api.service';
import { TenantService } from '../../core/services/tenant.service';

@Component({
  selector: 'app-configuracoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './configuracoes.component.html',
})
export class ConfiguracoesComponent implements OnInit, OnDestroy {
  @ViewChild('taxaDialog') taxaDialog?: ElementRef<HTMLElement>;

  aba: 'empresa' | 'pagamentos' = 'empresa';
  loading = true;
  salvandoEmpresa = false;
  criandoLoja = false;
  criandoAdquirente = false;
  salvandoTaxa = false;
  erro = '';
  sucesso = '';

  empresaNome = '';
  empresaDocumento = '';
  novaLojaNome = '';
  novaAdquirenteNome = '';
  adquirentes: Adquirente[] = [];
  taxas: ConfiguracaoTaxa[] = [];
  taxaEditandoId: number | null = null;
  editorTaxaAberto = false;
  filtroForma: 'TODAS' | FormaPagamento = 'TODAS';
  private focoAntesDoDialog: HTMLElement | null = null;

  taxaForm: ConfiguracaoTaxaPayload = this.novaTaxa();

  readonly formas: Array<{ value: FormaPagamento; label: string; help: string }> = [
    { value: 'DINHEIRO', label: 'Dinheiro', help: 'Recebido na hora e com troco.' },
    { value: 'PIX', label: 'Pix', help: 'Normalmente recebido na hora.' },
    { value: 'CARTAO_DEBITO', label: 'Cartão de débito', help: 'Taxa e prazo da maquininha.' },
    { value: 'CARTAO_CREDITO', label: 'Cartão de crédito', help: 'Taxa por número de parcelas.' },
  ];

  constructor(
    private readonly api: BusinessApiService,
    readonly tenant: TenantService,
  ) {}

  ngOnInit(): void {
    const contexto = this.tenant.contexto();
    if (contexto) {
      this.preencherEmpresa();
      this.carregarPagamentos();
      return;
    }
    this.tenant.carregar().subscribe({
      next: () => {
        this.preencherEmpresa();
        this.carregarPagamentos();
      },
      error: (error) => {
        this.loading = false;
        this.erro = error.error?.message || 'Não foi possível carregar as configurações.';
      },
    });
  }

  ngOnDestroy(): void {
    if (this.editorTaxaAberto) document.body.style.overflow = '';
  }

  get taxasFiltradas(): ConfiguracaoTaxa[] {
    return this.taxas.filter((taxa) =>
      this.filtroForma === 'TODAS' || taxa.formaPagamento === this.filtroForma,
    );
  }

  get taxaCartao(): boolean {
    return this.taxaForm.formaPagamento === 'CARTAO_DEBITO'
      || this.taxaForm.formaPagamento === 'CARTAO_CREDITO';
  }

  selecionarAba(aba: 'empresa' | 'pagamentos'): void {
    this.aba = aba;
    this.erro = '';
  }

  salvarEmpresa(): void {
    if (!this.empresaNome.trim() || this.salvandoEmpresa) return;
    this.salvandoEmpresa = true;
    this.api.atualizarEmpresa({
      nome: this.empresaNome.trim(),
      documento: this.empresaDocumento.trim(),
    }).subscribe({
      next: () => {
        this.salvandoEmpresa = false;
        this.feedback('Dados da empresa atualizados.');
        this.tenant.carregar().subscribe({ next: () => this.preencherEmpresa(), error: () => {} });
      },
      error: (error) => {
        this.salvandoEmpresa = false;
        this.erro = error.error?.message || 'Não foi possível atualizar a empresa.';
      },
    });
  }

  criarLoja(): void {
    if (!this.novaLojaNome.trim() || this.criandoLoja) return;
    this.criandoLoja = true;
    this.api.criarLoja({ nome: this.novaLojaNome.trim(), timezone: 'America/Sao_Paulo' }).subscribe({
      next: () => {
        this.criandoLoja = false;
        this.novaLojaNome = '';
        this.feedback('Nova loja criada. Ela já está disponível no seletor do topo.');
        this.tenant.carregar().subscribe();
      },
      error: (error) => {
        this.criandoLoja = false;
        this.erro = error.error?.message || 'Não foi possível criar a loja.';
      },
    });
  }

  criarAdquirente(): void {
    if (!this.novaAdquirenteNome.trim() || this.criandoAdquirente) return;
    this.criandoAdquirente = true;
    this.api.criarAdquirente(this.novaAdquirenteNome.trim()).subscribe({
      next: (adquirente) => {
        this.criandoAdquirente = false;
        this.novaAdquirenteNome = '';
        this.adquirentes = [...this.adquirentes, adquirente].sort((a, b) => a.nome.localeCompare(b.nome));
        this.feedback('Adquirente adicionada.');
      },
      error: (error) => {
        this.criandoAdquirente = false;
        this.erro = error.error?.message || 'Não foi possível criar a adquirente.';
      },
    });
  }

  abrirNovaTaxa(): void {
    this.taxaEditandoId = null;
    this.taxaForm = this.novaTaxa();
    this.abrirEditorTaxa();
  }

  editarTaxa(taxa: ConfiguracaoTaxa): void {
    this.taxaEditandoId = taxa.id;
    this.taxaForm = {
      formaPagamento: taxa.formaPagamento,
      adquirenteId: taxa.adquirenteId,
      bandeira: taxa.bandeira,
      parcelas: taxa.parcelas,
      taxaPercentual: Number(taxa.taxaPercentual),
      taxaFixa: Number(taxa.taxaFixa),
      prazoRecebimentoDias: taxa.prazoRecebimentoDias,
      ativo: taxa.ativo,
    };
    this.abrirEditorTaxa();
  }

  fecharEditorTaxa(): void {
    if (this.salvandoTaxa) return;
    this.editorTaxaAberto = false;
    this.taxaEditandoId = null;
    document.body.style.overflow = '';
    window.setTimeout(() => this.focoAntesDoDialog?.focus());
  }

  formaTaxaAlterada(): void {
    if (!this.taxaCartao) {
      this.taxaForm.adquirenteId = null;
      this.taxaForm.bandeira = null;
      this.taxaForm.parcelas = 1;
    }
    if (this.taxaForm.formaPagamento === 'CARTAO_DEBITO') this.taxaForm.parcelas = 1;
  }

  salvarTaxa(formulario: NgForm): void {
    if (formulario.invalid || !this.taxaFormularioValida()) {
      formulario.form.markAllAsTouched();
      this.erro = 'Revise os valores da regra de recebimento antes de salvar.';
      return;
    }
    if (this.salvandoTaxa) return;
    this.salvandoTaxa = true;
    this.erro = '';
    const request = this.taxaEditandoId === null
      ? this.api.criarTaxa(this.taxaForm)
      : this.api.atualizarTaxa(this.taxaEditandoId, this.taxaForm);
    request.subscribe({
      next: (salva) => {
        this.salvandoTaxa = false;
        const existe = this.taxas.some((item) => item.id === salva.id);
        this.taxas = existe
          ? this.taxas.map((item) => item.id === salva.id ? salva : item)
          : [...this.taxas, salva];
        this.ordenarTaxas();
        this.fecharEditorTaxa();
        this.feedback('Regra de recebimento salva. As próximas vendas usarão essa configuração.');
      },
      error: (error) => {
        this.salvandoTaxa = false;
        this.erro = error.error?.message || 'Não foi possível salvar a taxa.';
      },
    });
  }

  formaLabel(forma: FormaPagamento): string {
    return this.formas.find((item) => item.value === forma)?.label || forma;
  }

  fmtTaxa(valor: number): string {
    return Number(valor || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
  }

  taxaFormularioValida(): boolean {
    const percentual = Number(this.taxaForm.taxaPercentual);
    const taxaFixa = Number(this.taxaForm.taxaFixa);
    const prazo = Number(this.taxaForm.prazoRecebimentoDias);
    const parcelas = Number(this.taxaForm.parcelas);
    const casasValidas = (valor: number, casas: number): boolean => {
      const fator = 10 ** casas;
      return Math.abs(valor * fator - Math.round(valor * fator)) < 1e-6;
    };

    return Number.isFinite(percentual)
      && percentual >= 0
      && percentual <= 100
      && casasValidas(percentual, 4)
      && Number.isFinite(taxaFixa)
      && taxaFixa >= 0
      && taxaFixa <= 9_999_999_999.99
      && casasValidas(taxaFixa, 2)
      && Number.isInteger(prazo)
      && prazo >= 0
      && prazo <= 365
      && Number.isInteger(parcelas)
      && parcelas >= 1
      && parcelas <= 18
      && (this.taxaForm.formaPagamento === 'CARTAO_CREDITO' || parcelas === 1)
      && (this.taxaForm.bandeira?.length ?? 0) <= 40;
  }

  private preencherEmpresa(): void {
    const empresa = this.tenant.empresaAtual();
    this.empresaNome = empresa?.nome || '';
    this.empresaDocumento = empresa?.documento || '';
  }

  private carregarPagamentos(): void {
    this.loading = true;
    forkJoin({ taxas: this.api.listarTaxas(), adquirentes: this.api.listarAdquirentes() }).subscribe({
      next: ({ taxas, adquirentes }) => {
        this.taxas = taxas;
        this.adquirentes = adquirentes;
        this.ordenarTaxas();
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.erro = error.error?.message || 'Não foi possível carregar as regras de pagamento.';
      },
    });
  }

  private ordenarTaxas(): void {
    const ordem: FormaPagamento[] = ['DINHEIRO', 'PIX', 'CARTAO_DEBITO', 'CARTAO_CREDITO'];
    this.taxas = [...this.taxas].sort((a, b) =>
      ordem.indexOf(a.formaPagamento) - ordem.indexOf(b.formaPagamento)
      || a.parcelas - b.parcelas
      || (a.adquirenteNome || '').localeCompare(b.adquirenteNome || ''),
    );
  }

  @HostListener('document:keydown', ['$event'])
  gerenciarTecladoDoDialog(event: KeyboardEvent): void {
    if (!this.editorTaxaAberto) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.fecharEditorTaxa();
      return;
    }
    if (event.key === 'Tab' && this.taxaDialog) {
      this.manterFocoNoDialog(event, this.taxaDialog.nativeElement);
    }
  }

  private abrirEditorTaxa(): void {
    this.focoAntesDoDialog = document.activeElement as HTMLElement | null;
    this.editorTaxaAberto = true;
    document.body.style.overflow = 'hidden';
    window.setTimeout(() => {
      const dialog = this.taxaDialog?.nativeElement;
      dialog?.querySelector<HTMLElement>('select, input, button')?.focus();
    });
  }

  private manterFocoNoDialog(event: KeyboardEvent, dialog: HTMLElement): void {
    const elementos = Array.from(dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )).filter((elemento) => !elemento.hasAttribute('hidden'));
    if (elementos.length === 0) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    const primeiro = elementos[0];
    const ultimo = elementos[elementos.length - 1];
    if (event.shiftKey && document.activeElement === primeiro) {
      event.preventDefault();
      ultimo.focus();
    } else if (!event.shiftKey && document.activeElement === ultimo) {
      event.preventDefault();
      primeiro.focus();
    }
  }

  private novaTaxa(): ConfiguracaoTaxaPayload {
    return {
      formaPagamento: 'CARTAO_CREDITO',
      adquirenteId: null,
      bandeira: null,
      parcelas: 1,
      taxaPercentual: 3.5,
      taxaFixa: 0,
      prazoRecebimentoDias: 30,
      ativo: true,
    };
  }

  private feedback(mensagem: string): void {
    this.erro = '';
    this.sucesso = mensagem;
    window.setTimeout(() => (this.sucesso = ''), 6000);
  }
}
