import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Produto, ProdutoRequest, VendaRequest, VendaResponse,
  DashboardResponse, UsuarioResponse, UsuarioUpdateRequest,
  AlterarSenhaRequest, ImportResultDTO
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly API = 'https://dropsales.onrender.com/api';

  constructor(private http: HttpClient) {}

  // Dashboard
  getDashboard(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`${this.API}/dashboard`);
  }

  // Produtos
  getProdutos(): Observable<Produto[]> { return this.http.get<Produto[]>(`${this.API}/produtos`); }
  getProdutosEstoqueBaixo(): Observable<Produto[]> { return this.http.get<Produto[]>(`${this.API}/produtos/estoque-baixo`); }
  criarProduto(p: ProdutoRequest): Observable<Produto> { return this.http.post<Produto>(`${this.API}/produtos`, p); }
  atualizarProduto(id: number, p: ProdutoRequest): Observable<Produto> { return this.http.put<Produto>(`${this.API}/produtos/${id}`, p); }
  excluirProduto(id: number): Observable<void> { return this.http.delete<void>(`${this.API}/produtos/${id}`); }

  importarProdutos(file: File): Observable<ImportResultDTO> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResultDTO>(`${this.API}/produtos/import`, form);
  }

  // Vendas
  getVendas(): Observable<VendaResponse[]> { return this.http.get<VendaResponse[]>(`${this.API}/vendas`); }
  registrarVenda(v: VendaRequest): Observable<VendaResponse> { return this.http.post<VendaResponse>(`${this.API}/vendas`, v); }
  editarVenda(id: number, v: VendaRequest): Observable<VendaResponse> { return this.http.put<VendaResponse>(`${this.API}/vendas/${id}`, v); }
  cancelarVenda(id: number): Observable<void> { return this.http.delete<void>(`${this.API}/vendas/${id}`); }

  // Usuario / Perfil
  getMe(): Observable<UsuarioResponse> { return this.http.get<UsuarioResponse>(`${this.API}/usuarios/me`); }
  updateMe(data: UsuarioUpdateRequest): Observable<UsuarioResponse> { return this.http.put<UsuarioResponse>(`${this.API}/usuarios/me`, data); }
  getFotoUrl(): string { return `${this.API}/usuarios/me/foto`; }

  uploadFoto(file: File): Observable<UsuarioResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<UsuarioResponse>(`${this.API}/usuarios/me/foto`, form);
  }

  alterarSenha(data: AlterarSenhaRequest): Observable<void> {
    return this.http.put<void>(`${this.API}/usuarios/me/senha`, data);
  }
}