import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest } from '../models/api.models';
import { environment } from '@env/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  // Usa a fonte centralizada — nunca duplique a URL aqui
  private readonly API = environment.apiUrl;
  private readonly TOKEN_KEY = 'ds_token';
  private readonly USER_KEY = 'ds_user';

  // Header explícito para garantir Content-Type nas requisições de auth
  private readonly jsonHeaders = new HttpHeaders({ 'Content-Type': 'application/json' });

  private loggedIn$ = new BehaviorSubject<boolean>(this.hasToken());

  constructor(private http: HttpClient) {}

  login(data: LoginRequest): Observable<LoginResponse> {
    // CORRIGIDO: era "${this.API}/login" → endpoint real é /api/auth/login
    return this.http.post<LoginResponse>(`${this.API}/auth/login`, data, { headers: this.jsonHeaders }).pipe(
      tap((res) => {
        localStorage.setItem(this.TOKEN_KEY, res.token);
        localStorage.setItem(this.USER_KEY, JSON.stringify(res));
        this.loggedIn$.next(true);
      })
    );
  }

  register(data: RegisterRequest): Observable<any> {
    // CORRIGIDO: era "${this.API}/register" → endpoint real é /api/auth/register
    return this.http.post(`${this.API}/auth/register`, data, { headers: this.jsonHeaders });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.loggedIn$.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getUser(): LoginResponse | null {
    const raw = localStorage.getItem(this.USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  isLoggedIn(): boolean {
    return this.hasToken();
  }

  isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }
}
