import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { shouldEndSessionForUnauthorized } from '../utils/auth-token.utils';
import { isApiRequest } from '../utils/api-request.utils';
import { environment } from '@env/environment';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requestDaApi = isApiRequest(req.url, environment.apiUrl);
  const token = requestDaApi ? authService.getToken() : null;

  if (requestDaApi && token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse
          && shouldEndSessionForUnauthorized(
            error.status,
            req.url,
            token,
            authService.getToken(),
          )) {
        authService.logout();
        if (!router.url.startsWith('/login')) {
          void router.navigate(['/login'], { queryParams: { session: 'expired' } });
        }
      }

      return throwError(() => error);
    }),
  );
};
