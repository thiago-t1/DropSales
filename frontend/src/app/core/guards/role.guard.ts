import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { PapelEmpresa } from '../models/business.models';
import { TenantService } from '../services/tenant.service';
import { papelPermitido } from '../utils/role-permission.utils';

export const administrationGuard: CanActivateFn = () => {
  const tenant = inject(TenantService);
  const router = inject(Router);
  const permitidos: readonly PapelEmpresa[] = ['PROPRIETARIO', 'ADMINISTRADOR'];
  const contexto = tenant.contexto();

  if (contexto) {
    return papelPermitido(contexto.papelAtual, permitidos)
      ? true
      : router.createUrlTree(['/dashboard'], { queryParams: { acesso: 'restrito' } });
  }

  return tenant.carregar().pipe(
    map((carregado) => papelPermitido(carregado.papelAtual, permitidos)
      ? true
      : router.createUrlTree(['/dashboard'], { queryParams: { acesso: 'restrito' } })),
    catchError(() => of(router.createUrlTree(['/dashboard']))),
  );
};
