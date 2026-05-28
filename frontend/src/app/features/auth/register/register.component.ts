import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  form: FormGroup;
  loading = false;
  error = '';
  success = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      senha: ['', [Validators.required, Validators.minLength(6)]],
      confirmarSenha: ['', [Validators.required]],
    });
  }

  get senhasNaoBatem(): boolean {
    return this.form.get('senha')?.value !== this.form.get('confirmarSenha')?.value;
  }

  onSubmit(): void {
    if (this.form.invalid || this.senhasNaoBatem) return;
    this.loading = true;
    this.error = '';
    this.success = '';

    const { nome, email, senha } = this.form.value;

    this.authService.register({ nome, email, senha }).subscribe({
      next: () => {
        this.success = 'Conta criada com sucesso! Redirecionando...';
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.message || 'Erro ao criar conta. Tente novamente.';
      },
    });
  }
}
