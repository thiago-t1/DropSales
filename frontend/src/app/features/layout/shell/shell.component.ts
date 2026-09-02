import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { ThemeService } from '../../../core/services/theme.service';
import { VendaResponse } from '../../../core/models/api.models';
import { TenantService } from '../../../core/services/tenant.service';
import { ProfilePhotoService } from '../../../core/services/profile-photo.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
})
export class ShellComponent implements OnInit {
  sidebarOpen   = false;
  profileOpen   = false;
  notifOpen     = false;
  userName      = '';
  notifCount    = 0;
  vendasRecentes: VendaResponse[] = [];

  menuItems = [
    { label: 'Dashboard', mobileLabel: 'Início', icon: 'chart', route: '/dashboard', adminOnly: false },
    { label: 'Produtos', mobileLabel: 'Produtos', icon: 'package', route: '/produtos', adminOnly: false },
    { label: 'Vendas', mobileLabel: 'Vendas', icon: 'cart', route: '/vendas', adminOnly: false },
    { label: 'Caixa', mobileLabel: 'Caixa', icon: 'wallet', route: '/caixa', adminOnly: false },
    { label: 'Equipe', mobileLabel: 'Equipe', icon: 'users', route: '/equipe', adminOnly: true },
    { label: 'Configurações', mobileLabel: 'Ajustes', icon: 'settings', route: '/configuracoes', adminOnly: false },
  ];

  mobileMenuItems = this.menuItems.slice(0, 4);

  constructor(
    private authService: AuthService,
    private apiService: ApiService,
    private router: Router,
    readonly themeService: ThemeService,
    readonly tenantService: TenantService,
    readonly profilePhotoService: ProfilePhotoService,
  ) {
    const user = this.authService.getUser();
    this.userName = user?.nome ?? 'Usuário';
  }

  ngOnInit(): void {
    this.profilePhotoService.carregar();
    this.tenantService.carregar().subscribe({
      next: () => this.carregarNotificacoes(),
      error: () => this.carregarNotificacoes(),
    });
  }

  carregarNotificacoes(): void {
    this.apiService.getVendasRecentes().subscribe({
      next: (vendas) => {
        this.vendasRecentes = vendas;
        this.notifCount = this.vendasRecentes.length;
      },
      error: () => {},
    });
  }

  @HostListener('document:click', ['$event'])
  onDocClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('#notif-btn') && !target.closest('#notif-dropdown')) {
      this.notifOpen = false;
    }
    if (!target.closest('#profile-btn') && !target.closest('#profile-dropdown')) {
      this.profileOpen = false;
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.sidebarOpen = false;
    this.profileOpen = false;
    this.notifOpen = false;
    document.body.style.overflow = '';
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
    document.body.style.overflow = this.sidebarOpen ? 'hidden' : '';
  }

  closeSidebar(): void {
    this.sidebarOpen = false;
    document.body.style.overflow = '';
  }
  toggleNotif():    void {
    this.notifOpen = !this.notifOpen;
    this.profileOpen = false;
    if (this.notifOpen) {
      this.carregarNotificacoes();
    }
  }
  toggleProfile():  void { this.profileOpen   = !this.profileOpen; this.notifOpen = false; }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  logout(): void {
    this.profileOpen = false;
    this.profilePhotoService.limpar();
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  selecionarLoja(lojaId: string | number): void {
    this.tenantService.selecionarLoja(Number(lojaId));
  }

  papelLabel(): string {
    return this.tenantService.papelLabel();
  }

  fmt(v: number): string { return (v ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 2 }); }
  fmtData(d: string): string {
    if (!d) return '-';
    const dt = new Date(d);
    return dt.toLocaleDateString('pt-BR');
  }
}
