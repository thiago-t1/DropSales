import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
  showPassword = false;
  showConfirmPassword = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {
    this.form = this.fb.group({
      nome: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(150)]],
      nomeEmpresa: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(160)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(200)]],
      senha: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(72)]],
      confirmarSenha: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(72)]],
    });
  }

  get senhasNaoBatem(): boolean {
    return this.form.get('senha')?.value !== this.form.get('confirmarSenha')?.value;
  }

  onSubmit(): void {
    if (this.form.invalid || this.senhasNaoBatem) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.error = '';
    this.success = '';

    const { nome, nomeEmpresa, email, senha } = this.form.value;

    this.authService.register({ nome, nomeEmpresa, email, senha }).subscribe({
      next: () => {
        this.success = 'Conta criada com sucesso! Redirecionando...';
        const redirect = this.route.snapshot.queryParamMap.get('redirect');
        setTimeout(() => this.router.navigate(['/login'], {
          queryParams: redirect?.startsWith('/') ? { redirect } : undefined,
        }), 1500);
      },
      error: (err) => {
        this.loading = false;
        this.error = err.status === 0 || err.status >= 500
          ? 'Não foi possível conectar ao DropSales agora. Tente novamente em instantes.'
          : (err.error?.message || 'Não foi possível criar a conta. Confira os dados e tente novamente.');
      },
    });
  }
}
