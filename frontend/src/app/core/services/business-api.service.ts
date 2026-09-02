import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import {
  Adquirente,
  ConfiguracaoTaxa,
  ConfiguracaoTaxaPayload,
  ConviteEmpresa,
  EmpresaResumo,
  LojaResumo,
  MembroEmpresa,
  PapelEmpresa,
  ResumoRecebiveis,
  Recebivel,
} from '../models/business.models';

@Injectable({ providedIn: 'root' })
export class BusinessApiService {
  private readonly api = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  criarEmpresa(payload: { nome: string; documento?: string; nomeLoja?: string }): Observable<EmpresaResumo> {
    return this.http.post<EmpresaResumo>(`${this.api}/contexto/empresas`, payload);
  }

  atualizarEmpresa(payload: { nome: string; documento?: string; nomeLoja?: string }): Observable<EmpresaResumo> {
    return this.http.put<EmpresaResumo>(`${this.api}/contexto/empresa`, payload);
  }

  criarLoja(payload: { nome: string; timezone: string }): Observable<LojaResumo> {
    return this.http.post<LojaResumo>(`${this.api}/contexto/lojas`, payload);
  }

  listarMembros(): Observable<MembroEmpresa[]> {
    return this.http.get<MembroEmpresa[]>(`${this.api}/equipe/membros`);
  }

  atualizarMembro(id: number, papel: PapelEmpresa, ativo: boolean): Observable<MembroEmpresa> {
    return this.http.put<MembroEmpresa>(`${this.api}/equipe/membros/${id}`, { papel, ativo });
  }

  listarConvites(): Observable<ConviteEmpresa[]> {
    return this.http.get<ConviteEmpresa[]>(`${this.api}/equipe/convites`);
  }

  criarConvite(email: string, papel: PapelEmpresa): Observable<ConviteEmpresa> {
    return this.http.post<ConviteEmpresa>(`${this.api}/equipe/convites`, { email, papel });
  }

  revogarConvite(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/equipe/convites/${id}`);
  }

  visualizarConvite(token: string): Observable<ConviteEmpresa> {
    return this.http.get<ConviteEmpresa>(`${this.api}/auth/convites/${encodeURIComponent(token)}`);
  }

  aceitarConvite(token: string): Observable<ConviteEmpresa> {
    return this.http.post<ConviteEmpresa>(`${this.api}/equipe/convites/aceitar`, { token });
  }

  listarAdquirentes(): Observable<Adquirente[]> {
    return this.http.get<Adquirente[]>(`${this.api}/configuracoes/pagamentos/adquirentes`);
  }

  criarAdquirente(nome: string): Observable<Adquirente> {
    return this.http.post<Adquirente>(`${this.api}/configuracoes/pagamentos/adquirentes`, { nome });
  }

  listarTaxas(): Observable<ConfiguracaoTaxa[]> {
    return this.http.get<ConfiguracaoTaxa[]>(`${this.api}/configuracoes/pagamentos/taxas`);
  }

  criarTaxa(payload: ConfiguracaoTaxaPayload): Observable<ConfiguracaoTaxa> {
    return this.http.post<ConfiguracaoTaxa>(`${this.api}/configuracoes/pagamentos/taxas`, payload);
  }

  atualizarTaxa(id: number, payload: ConfiguracaoTaxaPayload): Observable<ConfiguracaoTaxa> {
    return this.http.put<ConfiguracaoTaxa>(`${this.api}/configuracoes/pagamentos/taxas/${id}`, payload);
  }

  listarRecebiveis(): Observable<ResumoRecebiveis> {
    return this.http.get<ResumoRecebiveis>(`${this.api}/recebiveis`);
  }

  confirmarRecebimento(id: number): Observable<Recebivel> {
    return this.http.post<Recebivel>(`${this.api}/recebiveis/${id}/receber`, {});
  }
}
