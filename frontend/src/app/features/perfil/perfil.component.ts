import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { UsuarioResponse, UsuarioUpdateRequest, AlterarSenhaRequest } from '../../core/models/api.models';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './perfil.component.html',
})
export class PerfilComponent implements OnInit {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

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

  // Preview da foto
  fotoPreview: string | null = null;
  readonly fotoUrl: string;

  constructor(private apiService: ApiService) {
    this.fotoUrl = this.apiService.getFotoUrl();
  }

  ngOnInit(): void {
    this.apiService.getMe().subscribe({
      next: (u) => {
        this.usuario = u;
        this.form = { nome: u.nome, email: u.email };
        this.loading = false;
      },
      error: () => { this.erro = 'Erro ao carregar perfil.'; this.loading = false; },
    });
  }

  abrirSeletor(): void { this.fileInput.nativeElement.click(); }

  onFotoSelecionada(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    // Preview local
    const reader = new FileReader();
    reader.onload = (e) => { this.fotoPreview = e.target?.result as string; };
    reader.readAsDataURL(file);

    // Upload
    this.uploadingFoto = true;
    this.apiService.uploadFoto(file).subscribe({
      next: (u) => {
        this.usuario = u;
        this.sucesso = 'Foto atualizada!';
        this.uploadingFoto = false;
        setTimeout(() => (this.sucesso = ''), 3000);
      },
      error: () => {
        this.fotoPreview = null;
        this.erro = 'Erro ao fazer upload da foto.';
        this.uploadingFoto = false;
        setTimeout(() => (this.erro = ''), 4000);
      },
    });
  }

  salvar(): void {
    if (!this.form.nome || !this.form.email) return;
    this.saving = true; this.sucesso = ''; this.erro = '';
    this.apiService.updateMe(this.form).subscribe({
      next: (u) => {
        this.usuario = u;
        this.sucesso = 'Perfil atualizado com sucesso!';
        this.saving = false;
        const raw = localStorage.getItem('ds_user');
        if (raw) {
          const user = JSON.parse(raw);
          user.nome = u.nome; user.email = u.email;
          localStorage.setItem('ds_user', JSON.stringify(user));
        }
        setTimeout(() => (this.sucesso = ''), 4000);
      },
      error: (e) => { this.erro = e.error?.message || 'Erro ao atualizar.'; this.saving = false; setTimeout(() => (this.erro = ''), 4000); },
    });
  }

  alterarSenha(): void {
    this.erroSenha = ''; this.sucessoSenha = '';
    if (this.senhaForm.novaSenha !== this.senhaForm.confirmarSenha) {
      this.erroSenha = 'As senhas nao coincidem.'; return;
    }
    if (this.senhaForm.novaSenha.length < 6) {
      this.erroSenha = 'Nova senha deve ter no minimo 6 caracteres.'; return;
    }
    this.savingSenha = true;
    this.apiService.alterarSenha(this.senhaForm).subscribe({
      next: () => {
        this.sucessoSenha = 'Senha alterada com sucesso!';
        this.senhaForm = { senhaAtual: '', novaSenha: '', confirmarSenha: '' };
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