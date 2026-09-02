import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, finalize, Observable, Subject, takeUntil, tap, throwError } from 'rxjs';
import { environment } from '@env/environment';
import { ContextoLoja, EmpresaResumo, LojaResumo, PapelEmpresa } from '../models/business.models';

export const ACTIVE_STORE_KEY = 'dropsales_active_store_id';

@Injectable({ providedIn: 'root' })
export class TenantService {
  private readonly api = environment.apiUrl;
  private readonly reiniciarContexto$ = new Subject<void>();
  private geracaoContexto = 0;
  readonly contexto = signal<ContextoLoja | null>(null);
  readonly carregando = signal(false);

  constructor(private readonly http: HttpClient) {}

  carregar(): Observable<ContextoLoja> {
    const geracaoDaRequisicao = this.geracaoContexto;
    this.carregando.set(true);
    return this.buscarContexto(true).pipe(
      takeUntil(this.reiniciarContexto$),
      tap((contexto) => {
        if (geracaoDaRequisicao !== this.geracaoContexto) return;
        this.contexto.set(contexto);
        if (!localStorage.getItem(ACTIVE_STORE_KEY)) {
          localStorage.setItem(ACTIVE_STORE_KEY, String(contexto.lojaAtualId));
        }
      }),
      finalize(() => {
        if (geracaoDaRequisicao === this.geracaoContexto) {
          this.carregando.set(false);
        }
      }),
    );
  }

  limpar(): void {
    this.geracaoContexto++;
    this.reiniciarContexto$.next();
    this.contexto.set(null);
    this.carregando.set(false);
    localStorage.removeItem(ACTIVE_STORE_KEY);
  }

  private buscarContexto(recuperarLojaInvalida: boolean): Observable<ContextoLoja> {
    return this.http.get<ContextoLoja>(`${this.api}/contexto`).pipe(
      catchError((error: HttpErrorResponse) => {
        const lojaSalva = localStorage.getItem(ACTIVE_STORE_KEY);
        const acessoInvalido = error.status === 403 || error.status === 404;
        if (recuperarLojaInvalida && lojaSalva && acessoInvalido) {
          localStorage.removeItem(ACTIVE_STORE_KEY);
          return this.buscarContexto(false);
        }
        return throwError(() => error);
      }),
    );
  }

  selecionarLoja(lojaId: number): void {
    const lojaExiste = this.lojasDisponiveis().some((loja) => loja.id === lojaId);
    if (!lojaExiste || this.lojaAtualId() === lojaId) return;
    localStorage.setItem(ACTIVE_STORE_KEY, String(lojaId));
    window.location.reload();
  }

  lojaAtualId(): number | null {
    const valor = localStorage.getItem(ACTIVE_STORE_KEY);
    if (!valor) return this.contexto()?.lojaAtualId ?? null;
    const id = Number(valor);
    return Number.isFinite(id) ? id : null;
  }

  lojaAtual(): LojaResumo | null {
    const id = this.lojaAtualId();
    return this.lojasDisponiveis().find((loja) => loja.id === id) ?? null;
  }

  empresaAtual(): EmpresaResumo | null {
    const contexto = this.contexto();
    const lojaId = this.lojaAtualId();
    return contexto?.empresas.find((empresa) =>
      empresa.lojas.some((loja) => loja.id === lojaId),
    ) ?? null;
  }

  lojasDisponiveis(): LojaResumo[] {
    return this.contexto()?.empresas.flatMap((empresa) => empresa.lojas) ?? [];
  }

  nomeEmpresaAtual(): string {
    return this.empresaAtual()?.nome?.trim() || 'Sua empresa';
  }

  nomeUnidade(nome?: string | null): string {
    const nomeNormalizado = nome?.trim();
    if (!nomeNormalizado || nomeNormalizado.toLocaleLowerCase('pt-BR') === 'loja principal') {
      return 'Unidade principal';
    }
    return nomeNormalizado;
  }

  nomeUnidadeAtual(): string {
    return this.nomeUnidade(this.lojaAtual()?.nome);
  }

  papelLabel(papel?: PapelEmpresa | null): string {
    const papelAtual = papel ?? this.empresaAtual()?.papel ?? this.contexto()?.papelAtual;
    return ({
      PROPRIETARIO: 'Proprietário',
      ADMINISTRADOR: 'Administrador',
      GERENTE: 'Gerente',
      OPERADOR: 'Operador',
    } as Record<PapelEmpresa, string>)[papelAtual as PapelEmpresa] ?? 'Minha conta';
  }

  podeGerenciar(): boolean {
    const papel = this.empresaAtual()?.papel ?? this.contexto()?.papelAtual;
    return papel === 'PROPRIETARIO' || papel === 'ADMINISTRADOR' || papel === 'GERENTE';
  }

  podeAdministrar(): boolean {
    const papel = this.empresaAtual()?.papel ?? this.contexto()?.papelAtual;
    return papel === 'PROPRIETARIO' || papel === 'ADMINISTRADOR';
  }
}
