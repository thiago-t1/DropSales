import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ConviteEmpresa, MembroEmpresa, PapelEmpresa } from '../../core/models/business.models';
import { BusinessApiService } from '../../core/services/business-api.service';
import { TenantService } from '../../core/services/tenant.service';

@Component({
  selector: 'app-equipe',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './equipe.component.html',
})
export class EquipeComponent implements OnInit, OnDestroy {
  @ViewChild('conviteDialog') conviteDialog?: ElementRef<HTMLElement>;

  membros: MembroEmpresa[] = [];
  convites: ConviteEmpresa[] = [];
  loading = true;
  salvandoId: number | null = null;
  revogandoId: number | null = null;
  convidando = false;
  erro = '';
  sucesso = '';
  emailConvite = '';
  papelConvite: PapelEmpresa = 'OPERADOR';
  conviteCriado: ConviteEmpresa | null = null;
  linkConvite = '';
  private focoAntesDoDialog: HTMLElement | null = null;

  readonly papeisEditaveis: Array<{ value: PapelEmpresa; label: string; description: string }> = [
    { value: 'ADMINISTRADOR', label: 'Administrador', description: 'Gerencia equipe, lojas e taxas.' },
    { value: 'GERENTE', label: 'Gerente', description: 'Opera a loja e confirma entradas no caixa.' },
    { value: 'OPERADOR', label: 'Operador', description: 'Produtos e vendas do dia a dia.' },
  ];

  constructor(
    private readonly api: BusinessApiService,
    readonly tenant: TenantService,
  ) {}

  ngOnInit(): void {
    if (!this.tenant.contexto()) this.tenant.carregar().subscribe({ error: () => {} });
    this.carregar();
  }

  ngOnDestroy(): void {
    if (this.conviteCriado) document.body.style.overflow = '';
  }

  carregar(): void {
    this.loading = true;
    this.erro = '';
    forkJoin({ membros: this.api.listarMembros(), convites: this.api.listarConvites() }).subscribe({
      next: ({ membros, convites }) => {
        this.membros = membros;
        this.convites = convites;
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.erro = error.error?.message || 'Não foi possível carregar a equipe.';
      },
    });
  }

  get membrosAtivos(): number {
    return this.membros.filter((membro) => membro.ativo).length;
  }

  get gestoresAtivos(): number {
    return this.membros.filter((membro) =>
      membro.ativo && ['PROPRIETARIO', 'ADMINISTRADOR', 'GERENTE'].includes(membro.papel),
    ).length;
  }

  enviarConvite(formulario: NgForm): void {
    const email = this.emailConvite.trim().toLowerCase();
    if (formulario.invalid || !email || this.convidando) {
      formulario.form.markAllAsTouched();
      if (!this.convidando) this.erro = 'Informe um e-mail válido para criar o convite.';
      return;
    }
    this.convidando = true;
    this.erro = '';
    this.api.criarConvite(email, this.papelConvite).subscribe({
      next: (convite) => {
        this.convidando = false;
        this.focoAntesDoDialog = document.activeElement as HTMLElement | null;
        this.conviteCriado = convite;
        this.linkConvite = this.criarLink(convite.token || '');
        document.body.style.overflow = 'hidden';
        window.setTimeout(() => {
          this.conviteDialog?.nativeElement
            .querySelector<HTMLElement>('input, button')
            ?.focus();
        });
        this.emailConvite = '';
        this.papelConvite = 'OPERADOR';
        this.sucesso = 'Convite criado. Copie o link e envie para a pessoa.';
        this.api.listarConvites().subscribe((convites) => (this.convites = convites));
      },
      error: (error) => {
        this.convidando = false;
        this.erro = error.error?.message || 'Não foi possível criar o convite.';
      },
    });
  }

  atualizarPapel(membro: MembroEmpresa, papel: PapelEmpresa): void {
    if (membro.papel === papel || this.salvandoId !== null) return;
    this.salvandoId = membro.id;
    this.api.atualizarMembro(membro.id, papel, membro.ativo).subscribe({
      next: (atualizado) => {
        this.salvandoId = null;
        this.substituirMembro(atualizado);
        this.feedback(`${atualizado.nome} agora é ${this.papelLabel(atualizado.papel).toLowerCase()}.`);
      },
      error: (error) => {
        this.salvandoId = null;
        this.erro = error.error?.message || 'Não foi possível alterar a permissão.';
      },
    });
  }

  alternarAcesso(membro: MembroEmpresa): void {
    if (membro.usuarioAtual || membro.papel === 'PROPRIETARIO' || this.salvandoId !== null) return;
    const acao = membro.ativo ? 'suspender' : 'reativar';
    if (!window.confirm(`Deseja ${acao} o acesso de ${membro.nome}?`)) return;
    this.salvandoId = membro.id;
    this.api.atualizarMembro(membro.id, membro.papel, !membro.ativo).subscribe({
      next: (atualizado) => {
        this.salvandoId = null;
        this.substituirMembro(atualizado);
        this.feedback(atualizado.ativo ? 'Acesso reativado.' : 'Acesso suspenso.');
      },
      error: (error) => {
        this.salvandoId = null;
        this.erro = error.error?.message || 'Não foi possível alterar o acesso.';
      },
    });
  }

  revogar(convite: ConviteEmpresa): void {
    if (this.revogandoId !== null || !window.confirm(`Revogar o convite enviado para ${convite.email}?`)) return;
    this.revogandoId = convite.id;
    this.api.revogarConvite(convite.id).subscribe({
      next: () => {
        this.revogandoId = null;
        this.convites = this.convites.filter((item) => item.id !== convite.id);
        this.feedback('Convite revogado.');
      },
      error: (error) => {
        this.revogandoId = null;
        this.erro = error.error?.message || 'Não foi possível revogar o convite.';
      },
    });
  }

  async copiarLink(): Promise<void> {
    if (!this.linkConvite) return;
    try {
      await navigator.clipboard.writeText(this.linkConvite);
      this.feedback('Link copiado para a área de transferência.');
    } catch {
      this.erro = 'Não foi possível copiar automaticamente. Selecione o link e copie.';
    }
  }

  fecharConviteCriado(): void {
    this.conviteCriado = null;
    this.linkConvite = '';
    document.body.style.overflow = '';
    window.setTimeout(() => this.focoAntesDoDialog?.focus());
  }

  @HostListener('document:keydown', ['$event'])
  gerenciarTecladoDoDialog(event: KeyboardEvent): void {
    if (!this.conviteCriado) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.fecharConviteCriado();
      return;
    }
    if (event.key !== 'Tab' || !this.conviteDialog) return;

    const dialog = this.conviteDialog.nativeElement;
    const elementos = Array.from(dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ));
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

  papelLabel(papel: PapelEmpresa): string {
    return {
      PROPRIETARIO: 'Proprietário',
      ADMINISTRADOR: 'Administrador',
      GERENTE: 'Gerente',
      OPERADOR: 'Operador',
    }[papel];
  }

  iniciais(nome: string): string {
    return nome.split(' ').filter(Boolean).slice(0, 2).map((parte) => parte[0].toUpperCase()).join('');
  }

  fmtData(data: string): string {
    return new Date(data).toLocaleDateString('pt-BR');
  }

  private criarLink(token: string): string {
    return new URL(`convite/${encodeURIComponent(token)}`, document.baseURI).toString();
  }

  private substituirMembro(atualizado: MembroEmpresa): void {
    this.membros = this.membros.map((item) => item.id === atualizado.id ? atualizado : item);
  }

  private feedback(mensagem: string): void {
    this.erro = '';
    this.sucesso = mensagem;
    window.setTimeout(() => (this.sucesso = ''), 5000);
  }
}
