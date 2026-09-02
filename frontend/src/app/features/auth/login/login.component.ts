import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  form: FormGroup;
  loading = false;
  error = '';
  showPassword = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email, Validators.maxLength(200)]],
      senha: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.error = '';

    this.authService.login(this.form.value).subscribe({
      next: () => {
        const redirect = this.route.snapshot.queryParamMap.get('redirect');
        this.router.navigateByUrl(redirect?.startsWith('/') ? redirect : '/dashboard');
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 0 || err.status >= 500) {
          this.error = 'Não foi possível conectar ao DropSales agora. Tente novamente em instantes.';
          return;
        }
        this.error = err.status === 401 || err.status === 403
          ? 'E-mail ou senha incorretos. Confira os dados e tente novamente.'
          : (err.error?.message || 'Não foi possível entrar. Tente novamente.');
      },
    });
  }
}
