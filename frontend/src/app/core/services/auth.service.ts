import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest } from '../models/api.models';
import { environment } from '@env/environment';
import { isJwtUsable } from '../utils/auth-token.utils';
import { TenantService } from './tenant.service';

type StoredUser = Omit<LoginResponse, 'token'>;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = environment.apiUrl;
  private readonly TOKEN_KEY = 'ds_token';
  private readonly USER_KEY = 'ds_user';

  private readonly jsonHeaders = new HttpHeaders({ 'Content-Type': 'application/json' });

  private loggedIn$ = new BehaviorSubject<boolean>(isJwtUsable(this.getToken()));

  constructor(
    private readonly http: HttpClient,
    private readonly tenantService: TenantService,
  ) {}

  login(data: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API}/auth/login`, data, { headers: this.jsonHeaders }).pipe(
      tap((res) => {
        const { token, ...user } = res;
        this.tenantService.limpar();
        localStorage.setItem(this.TOKEN_KEY, token);
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
        this.loggedIn$.next(true);
      })
    );
  }

  register(data: RegisterRequest): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API}/auth/register`, data, { headers: this.jsonHeaders });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.tenantService.limpar();
    this.loggedIn$.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  replaceToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    this.loggedIn$.next(true);
  }

  getUser(): StoredUser | null {
    const raw = localStorage.getItem(this.USER_KEY);
    if (!raw) return null;

    try {
      const parsed = JSON.parse(raw) as Partial<LoginResponse>;
      if (typeof parsed.nome !== 'string'
          || typeof parsed.email !== 'string'
          || typeof parsed.perfil !== 'string') {
        throw new Error('Stored user is invalid');
      }
      const user: StoredUser = {
        nome: parsed.nome,
        email: parsed.email,
        perfil: parsed.perfil,
      };
      if ('token' in parsed) {
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
      }
      return user;
    } catch {
      localStorage.removeItem(this.USER_KEY);
      return null;
    }
  }

  updateStoredUser(data: Pick<StoredUser, 'nome' | 'email'>): void {
    const user = this.getUser();
    if (!user) return;
    localStorage.setItem(this.USER_KEY, JSON.stringify({ ...user, ...data }));
  }

  isLoggedIn(): boolean {
    const valid = isJwtUsable(this.getToken());
    if (!valid && this.getToken()) this.logout();
    return valid;
  }

  isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }
}
