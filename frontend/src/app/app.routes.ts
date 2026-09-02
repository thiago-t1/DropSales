import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { pendingChangesGuard } from './core/guards/pending-changes.guard';
import { administrationGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
  },
  {
    path: 'convite/:token',
    loadComponent: () =>
      import('./features/auth/convite/convite.component').then(m => m.ConviteComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./features/layout/shell/shell.component').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'produtos',
        loadComponent: () =>
          import('./features/produtos/produtos.component').then(m => m.ProdutosComponent),
      },
      {
        path: 'vendas',
        loadComponent: () =>
          import('./features/vendas/vendas.component').then(m => m.VendasComponent),
        canDeactivate: [pendingChangesGuard],
      },
      {
        path: 'caixa',
        loadComponent: () =>
          import('./features/caixa/caixa.component').then(m => m.CaixaComponent),
      },
      { path: 'recebimentos', redirectTo: 'caixa', pathMatch: 'full' },
      { path: 'recebiveis', redirectTo: 'caixa', pathMatch: 'full' },
      {
        path: 'equipe',
        canActivate: [administrationGuard],
        loadComponent: () =>
          import('./features/equipe/equipe.component').then(m => m.EquipeComponent),
      },
      {
        path: 'configuracoes',
        loadComponent: () =>
          import('./features/configuracoes/configuracoes.component').then(m => m.ConfiguracoesComponent),
      },
      {
        path: 'perfil',
        loadComponent: () =>
          import('./features/perfil/perfil.component').then(m => m.PerfilComponent),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
