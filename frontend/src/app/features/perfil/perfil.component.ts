import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OnDestroy } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { UsuarioResponse, UsuarioUpdateRequest, AlterarSenhaRequest } from '../../core/models/api.models';
import { ProfilePhotoService } from '../../core/services/profile-photo.service';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './perfil.component.html',
})
export class PerfilComponent implements OnInit, OnDestroy {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  private readonly maxFotoBytes = 5 * 1024 * 1024;
  private readonly tiposFotoPermitidos = ['image/jpeg', 'image/png', 'image/webp'];

  usuario: UsuarioResponse | null = null;
  form: UsuarioUpdateRequest = { nome: '', email: '' };
  senhaForm: AlterarSenhaRequest = { senhaAtual: '', novaSenha: '', confirmarSenha: '' };

  loading   = true;
  saving    = false;
  savingSenha = false;
  sucesso   = '';
  erro      = '';
  sucessoSenha = '';
  erroSenha = '';
  uploadingFoto = false;
  mostrarSenhaAtual = false;
  mostrarNovaSenha = false;
  mostrarConfirmacaoSenha = false;

  fotoPreview: string | null = null;

  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private readonly tenantService: TenantService,
    readonly profilePhotoService: ProfilePhotoService,
  ) {}

  ngOnInit(): void {
    this.apiService.getMe().subscribe({
      next: (u) => {
        this.usuario = u;
        this.form = { nome: u.nome, email: u.email };
        this.loading = false;
        if (u.temFoto) this.profilePhotoService.carregar();
      },
      error: () => { this.erro = 'Erro ao carregar perfil.'; this.loading = false; },
    });
  }

  ngOnDestroy(): void {
    this.revogarFotoPreview();
  }

  get inicialUsuario(): string {
    return (this.form.nome || this.usuario?.nome || 'U').trim().charAt(0).toUpperCase() || 'U';
  }

  get perfilLabel(): string {
    if (this.tenantService.contexto()) return this.tenantService.papelLabel();
    if (this.usuario?.perfil === 'ADMIN') return 'Administrador';
    if (this.usuario?.perfil === 'OPERADOR') return 'Operador';
    if (this.usuario?.perfil === 'USUARIO') return 'Usuário';
    return this.usuario?.perfil || 'Usuário';
  }

  abrirSeletor(): void {
    if (!this.uploadingFoto) this.fileInput.nativeElement.click();
  }

  onFotoRemotaErro(): void {
    this.profilePhotoService.limpar();
  }

  onFotoSelecionada(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.erro = '';
    this.sucesso = '';

    if (!this.tiposFotoPermitidos.includes(file.type)) {
      this.erro = 'Escolha uma imagem JPG, PNG ou WebP.';
      input.value = '';
      return;
    }

    if (file.size > this.maxFotoBytes) {
      this.erro = 'A imagem deve ter no máximo 5 MB.';
      input.value = '';
      return;
    }

    this.revogarFotoPreview();
    this.fotoPreview = URL.createObjectURL(file);

    this.uploadingFoto = true;
    this.apiService.uploadFoto(file).subscribe({
      next: (u) => {
        this.usuario = u;
        this.profilePhotoService.definir(file);
        this.revogarFotoPreview();
        this.sucesso = 'Foto atualizada com sucesso.';
        this.uploadingFoto = false;
        input.value = '';
        setTimeout(() => (this.sucesso = ''), 3000);
      },
      error: () => {
        this.revogarFotoPreview();
        this.erro = 'Não foi possível atualizar a foto. Tente novamente.';
        this.uploadingFoto = false;
        input.value = '';
        setTimeout(() => (this.erro = ''), 4000);
      },
    });
  }

  private revogarFotoPreview(): void {
    if (this.fotoPreview) URL.revokeObjectURL(this.fotoPreview);
    this.fotoPreview = null;
  }

  salvar(dadosForm?: NgForm): void {
    if (dadosForm?.invalid || !this.form.nome.trim() || !this.form.email.trim()) {
      dadosForm?.form.markAllAsTouched();
      this.erro = 'Revise os dados destacados antes de salvar.';
      return;
    }
    this.form = { nome: this.form.nome.trim(), email: this.form.email.trim() };
    this.saving = true; this.sucesso = ''; this.erro = '';
    this.apiService.updateMe(this.form).subscribe({
      next: (u) => {
        this.usuario = u;
        if (u.token) this.authService.replaceToken(u.token);
        this.sucesso = 'Perfil atualizado com sucesso!';
        this.saving = false;
        this.authService.updateStoredUser({ nome: u.nome, email: u.email });
        setTimeout(() => (this.sucesso = ''), 4000);
      },
      error: (e) => { this.erro = e.error?.message || 'Erro ao atualizar.'; this.saving = false; setTimeout(() => (this.erro = ''), 4000); },
    });
  }

  alterarSenha(senhaNgForm?: NgForm): void {
    this.erroSenha = ''; this.sucessoSenha = '';
    if (senhaNgForm?.invalid || !this.senhaForm.senhaAtual || !this.senhaForm.novaSenha || !this.senhaForm.confirmarSenha) {
      senhaNgForm?.form.markAllAsTouched();
      this.erroSenha = 'Preencha os três campos de senha.';
      return;
    }
    if (this.senhaForm.novaSenha !== this.senhaForm.confirmarSenha) {
      this.erroSenha = 'As senhas não coincidem.'; return;
    }
    if (this.senhaForm.novaSenha.length < 12) {
      this.erroSenha = 'A nova senha deve ter no mínimo 12 caracteres.'; return;
    }
    this.savingSenha = true;
    this.apiService.alterarSenha(this.senhaForm).subscribe({
      next: () => {
        this.sucessoSenha = 'Senha alterada com sucesso!';
        this.senhaForm = { senhaAtual: '', novaSenha: '', confirmarSenha: '' };
        senhaNgForm?.resetForm(this.senhaForm);
        this.mostrarSenhaAtual = false;
        this.mostrarNovaSenha = false;
        this.mostrarConfirmacaoSenha = false;
        this.savingSenha = false;
        setTimeout(() => (this.sucessoSenha = ''), 4000);
      },
      error: (e) => {
        this.erroSenha = e.status === 401 ? 'Senha atual incorreta.' : (e.error?.message || 'Erro ao alterar senha.');
        this.savingSenha = false;
        setTimeout(() => (this.erroSenha = ''), 5000);
      },
    });
  }
}
