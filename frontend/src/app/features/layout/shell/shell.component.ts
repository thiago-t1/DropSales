import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { VendaResponse } from '../../../core/models/api.models';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
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
    { label: 'Dashboard', icon: 'chart', route: '/dashboard' },
    { label: 'Produtos',  icon: 'package', route: '/produtos' },
    { label: 'Vendas',    icon: 'cart',    route: '/vendas' },
  ];

  constructor(
    private authService: AuthService,
    private apiService: ApiService,
    private router: Router,
  ) {
    const user = this.authService.getUser();
    this.userName = user?.nome ?? 'Usuario';
  }

  ngOnInit(): void {
    this.carregarNotificacoes();
  }

  carregarNotificacoes(): void {
    this.apiService.getVendas().subscribe({
      next: (vendas) => {
        this.vendasRecentes = vendas.slice(0, 5);
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

  toggleSidebar():  void { this.sidebarOpen  = !this.sidebarOpen; }
  toggleNotif():    void {
    this.notifOpen = !this.notifOpen;
    this.profileOpen = false;
    if (this.notifOpen) {
      this.carregarNotificacoes();
    }
  }
  toggleProfile():  void { this.profileOpen   = !this.profileOpen; this.notifOpen = false; }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  fmt(v: number): string { return (v ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 2 }); }
  fmtData(d: string): string {
    if (!d) return '-';
    const dt = new Date(d);
    return dt.toLocaleDateString('pt-BR');
  }
}