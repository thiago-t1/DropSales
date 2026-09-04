import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, shareReplay, tap, throwError } from 'rxjs';
import {
  Produto, ProdutoRequest, VendaRequest, VendaResponse,
  DashboardResponse, UsuarioResponse, UsuarioUpdateRequest,
  AlterarSenhaRequest, ImportResultDTO, VendaRecente
} from '../models/api.models';
import { environment } from '@env/environment';
import { ACTIVE_STORE_KEY } from './tenant.service';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly API = environment.apiUrl;
  private readonly dashboardCacheTtlMs = 30_000;
  private dashboardCache: {
    chave: string;
    expiraEm: number;
    request: Observable<DashboardResponse>;
    geracao: number;
  } | null = null;
  private dashboardCacheGeracao = 0;
  readonly atividadeVersao = signal(0);

  constructor(private http: HttpClient) {}

  // Dashboard
  warmup(): Observable<void> {
    return this.http.get<void>(`${this.API}/health`);
  }

  getDashboard(force = false): Observable<DashboardResponse> {
    const loja = localStorage.getItem(ACTIVE_STORE_KEY) ?? 'padrao';
    const sessao = localStorage.getItem('ds_token') ?? 'anonima';
    const chave = `${sessao}:${loja}`;
    const agora = Date.now();
    if (!force
        && this.dashboardCache?.chave === chave
        && this.dashboardCache.expiraEm > agora) {
      return this.dashboardCache.request;
    }

    const geracao = ++this.dashboardCacheGeracao;
    const request = this.http.get<DashboardResponse>(`${this.API}/dashboard`).pipe(
      catchError((erro) => {
        if (this.dashboardCache?.geracao === geracao) this.dashboardCache = null;
        return throwError(() => erro);
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.dashboardCache = {
      chave,
      expiraEm: agora + this.dashboardCacheTtlMs,
      request,
      geracao,
    };
    return request;
  }

  invalidarDashboard(): void {
    this.dashboardCacheGeracao++;
    this.dashboardCache = null;
  }

  private registrarAlteracaoDeVenda(): void {
    this.invalidarDashboard();
    this.atividadeVersao.update((versao) => versao + 1);
  }

  // Produtos
  getProdutos(): Observable<Produto[]> { return this.http.get<Produto[]>(`${this.API}/produtos`); }
  getProdutosEstoqueBaixo(): Observable<Produto[]> { return this.http.get<Produto[]>(`${this.API}/produtos/estoque-baixo`); }
  criarProduto(p: ProdutoRequest): Observable<Produto> {
    return this.http.post<Produto>(`${this.API}/produtos`, p)
      .pipe(tap(() => this.invalidarDashboard()));
  }
  atualizarProduto(id: number, p: ProdutoRequest): Observable<Produto> {
    return this.http.put<Produto>(`${this.API}/produtos/${id}`, p)
      .pipe(tap(() => this.invalidarDashboard()));
  }
  excluirProduto(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/produtos/${id}`)
      .pipe(tap(() => this.invalidarDashboard()));
  }

  importarProdutos(file: File): Observable<ImportResultDTO> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResultDTO>(`${this.API}/produtos/import`, form)
      .pipe(tap(() => this.invalidarDashboard()));
  }

  // Vendas
  getVendas(): Observable<VendaResponse[]> { return this.http.get<VendaResponse[]>(`${this.API}/vendas`); }
  getVendasRecentes(): Observable<VendaResponse[]> {
    return this.http.get<VendaResponse[]>(`${this.API}/vendas/recentes`);
  }
  getAtividadesRecentes(): Observable<VendaRecente[]> {
    return this.http.get<VendaRecente[]>(`${this.API}/dashboard/atividades-recentes`);
  }
  registrarVenda(v: VendaRequest, idempotencyKey: string): Observable<VendaResponse> {
    return this.http.post<VendaResponse>(`${this.API}/vendas`, v, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }).pipe(tap(() => this.registrarAlteracaoDeVenda()));
  }
  editarVenda(id: number, v: VendaRequest): Observable<VendaResponse> {
    return this.http.put<VendaResponse>(`${this.API}/vendas/${id}`, v)
      .pipe(tap(() => this.registrarAlteracaoDeVenda()));
  }
  cancelarVenda(id: number, motivo: string): Observable<VendaResponse> {
    return this.http.patch<VendaResponse>(`${this.API}/vendas/${id}/cancelar`, { motivo })
      .pipe(tap(() => this.registrarAlteracaoDeVenda()));
  }

  // Usuario / Perfil
  getMe(): Observable<UsuarioResponse> { return this.http.get<UsuarioResponse>(`${this.API}/usuarios/me`); }
  updateMe(data: UsuarioUpdateRequest): Observable<UsuarioResponse> { return this.http.put<UsuarioResponse>(`${this.API}/usuarios/me`, data); }
  getFoto(): Observable<Blob> {
    return this.http.get(`${this.API}/usuarios/me/foto`, { responseType: 'blob' });
  }

  uploadFoto(file: File): Observable<UsuarioResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<UsuarioResponse>(`${this.API}/usuarios/me/foto`, form);
  }

  alterarSenha(data: AlterarSenhaRequest): Observable<void> {
    return this.http.put<void>(`${this.API}/usuarios/me/senha`, data);
  }
}
