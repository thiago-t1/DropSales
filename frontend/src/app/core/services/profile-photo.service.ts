import { Injectable, OnDestroy, signal } from '@angular/core';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class ProfilePhotoService implements OnDestroy {
  private readonly fotoUrlState = signal<string | null>(null);
  readonly fotoUrl = this.fotoUrlState.asReadonly();

  private versaoRequisicao = 0;
  private carregando = false;
  private carregada = false;

  constructor(private readonly apiService: ApiService) {}

  carregar(force = false): void {
    if ((!force && this.carregada) || this.carregando) return;
    const versaoAtual = ++this.versaoRequisicao;
    this.carregando = true;

    this.apiService.getFoto().subscribe({
      next: (foto) => {
        if (versaoAtual !== this.versaoRequisicao) return;
        this.carregando = false;
        this.carregada = true;
        this.substituirFoto(foto);
      },
      error: () => {
        if (versaoAtual !== this.versaoRequisicao) return;
        this.carregando = false;
        this.carregada = true;
        this.limparUrl();
      },
    });
  }

  definir(foto: Blob): void {
    this.versaoRequisicao++;
    this.carregando = false;
    this.carregada = true;
    this.substituirFoto(foto);
  }

  limpar(): void {
    this.versaoRequisicao++;
    this.carregando = false;
    this.carregada = false;
    this.limparUrl();
  }

  ngOnDestroy(): void {
    this.limpar();
  }

  private substituirFoto(foto: Blob): void {
    const novaUrl = URL.createObjectURL(foto);
    const urlAnterior = this.fotoUrlState();
    this.fotoUrlState.set(novaUrl);
    if (urlAnterior) URL.revokeObjectURL(urlAnterior);
  }

  private limparUrl(): void {
    const urlAtual = this.fotoUrlState();
    this.fotoUrlState.set(null);
    if (urlAtual) URL.revokeObjectURL(urlAtual);
  }
}
