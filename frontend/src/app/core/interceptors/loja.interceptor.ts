import { HttpInterceptorFn } from '@angular/common/http';
import { ACTIVE_STORE_KEY } from '../services/tenant.service';
import { environment } from '@env/environment';
import { isApiRequest } from '../utils/api-request.utils';

export const lojaInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req.url, environment.apiUrl)) return next(req);
  const lojaId = localStorage.getItem(ACTIVE_STORE_KEY);
  if (!lojaId || !/^\d+$/.test(lojaId)) return next(req);
  return next(req.clone({ setHeaders: { 'X-Loja-Id': lojaId } }));
};
