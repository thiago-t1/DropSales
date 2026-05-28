import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = 'https://dropsales.onrender.com/api';
  private readonly TOKEN_KEY = 'ds_token';
  private readonly USER_KEY = 'ds_user';

  private loggedIn$ = new BehaviorSubject<boolean>(this.hasToken());

  constructor(private http: HttpClient) {}

  login(data: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.API}/login`, data).pipe(
      tap((res) => {
        localStorage.setItem(this.TOKEN_KEY, res.token);
        localStorage.setItem(this.USER_KEY, JSON.stringify(res));
        this.loggedIn$.next(true);
      })
    );
  }

  register(data: RegisterRequest): Observable<any> {
    return this.http.post(`${this.API}/register`, data);
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
