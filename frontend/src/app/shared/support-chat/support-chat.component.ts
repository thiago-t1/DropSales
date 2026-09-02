import { CommonModule } from '@angular/common';
import { Component, HostListener, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

const BOTPRESS_CHAT_URL = 'https://cdn.botpress.cloud/webchat/v3.7/shareable.html?configUrl=https://files.bpcontent.cloud/2026/04/07/22/20260407223643-NMTEP1F5.json';

@Component({
  selector: 'app-support-chat',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './support-chat.component.html',
  styleUrl: './support-chat.component.css',
})
export class SupportChatComponent {
  private readonly sanitizer = inject(DomSanitizer);
  readonly aberto = signal(false);
  readonly carregado = signal(false);
  readonly chatUrl: SafeResourceUrl = this.sanitizer.bypassSecurityTrustResourceUrl(BOTPRESS_CHAT_URL);

  alternar(): void {
    this.aberto.update((valor) => !valor);
  }

  fechar(): void {
    this.aberto.set(false);
  }

  marcarCarregado(): void {
    this.carregado.set(true);
  }

  @HostListener('document:keydown.escape')
  fecharComEscape(): void {
    if (this.aberto()) this.fechar();
  }
}
