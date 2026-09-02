import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ConviteEmpresa, PapelEmpresa } from '../../../core/models/business.models';
import { AuthService } from '../../../core/services/auth.service';
import { BusinessApiService } from '../../../core/services/business-api.service';
import { ACTIVE_STORE_KEY } from '../../../core/services/tenant.service';

@Component({
  selector: 'app-convite',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './convite.component.html',
})
export class ConviteComponent implements OnInit {
  token = '';
  convite: ConviteEmpresa | null = null;
  loading = true;
  aceitando = false;
  aceito = false;
  erro = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly auth: AuthService,
    private readonly api: BusinessApiService,
  ) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') || '';
    if (!this.token) {
      this.loading = false;
      this.erro = 'Este link de convite é inválido.';
      return;
    }
    this.api.visualizarConvite(this.token).subscribe({
      next: (convite) => {
        this.convite = convite;
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.erro = error.error?.message || 'Este convite não está mais disponível.';
      },
    });
  }

  get logado(): boolean {
    return this.auth.isLoggedIn();
  }

  get caminhoAtual(): string {
    return `/convite/${this.token}`;
  }

  aceitar(): void {
    if (!this.logado || this.aceitando) return;
    this.aceitando = true;
    this.erro = '';
    this.api.aceitarConvite(this.token).subscribe({
      next: (convite) => {
        this.aceitando = false;
        this.aceito = true;
        if (convite.lojaId != null) {
          localStorage.setItem(ACTIVE_STORE_KEY, String(convite.lojaId));
        } else {
          localStorage.removeItem(ACTIVE_STORE_KEY);
        }
      },
      error: (error) => {
        this.aceitando = false;
        this.erro = error.error?.message || 'Não foi possível aceitar o convite.';
      },
    });
  }

  irParaPainel(): void {
    void this.router.navigate(['/dashboard']);
  }

  papelLabel(papel: PapelEmpresa): string {
    return {
      PROPRIETARIO: 'Proprietário',
      ADMINISTRADOR: 'Administrador',
      GERENTE: 'Gerente',
      OPERADOR: 'Operador',
    }[papel];
  }
}
