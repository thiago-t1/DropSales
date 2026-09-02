import { Injectable, OnDestroy, signal } from '@angular/core';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class ProfilePhotoService implements OnDestroy {
  private readonly fotoUrlState = signal<string | null>(null);
  readonly fotoUrl = this.fotoUrlState.asReadonly();

  private versaoRequisicao = 0;

  constructor(private readonly apiService: ApiService) {}

  carregar(): void {
    const versaoAtual = ++this.versaoRequisicao;

    this.apiService.getFoto().subscribe({
      next: (foto) => {
        if (versaoAtual !== this.versaoRequisicao) return;
        this.substituirFoto(foto);
      },
      error: () => {
        if (versaoAtual !== this.versaoRequisicao) return;
        this.limparUrl();
      },
    });
  }

  definir(foto: Blob): void {
    this.versaoRequisicao++;
    this.substituirFoto(foto);
  }

  limpar(): void {
    this.versaoRequisicao++;
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
