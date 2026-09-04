import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/services/theme.service';
import { SupportChatComponent } from './shared/support-chat/support-chat.component';
import { ApiService } from './core/services/api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SupportChatComponent],
  template: `<router-outlet /><app-support-chat />`,
})
export class AppComponent {
  constructor(
    private readonly themeService: ThemeService,
    private readonly apiService: ApiService,
  ) {
    this.themeService.initialize();
    // Inicia o backend enquanto a pessoa lê/preenche a primeira tela, sem
    // bloquear o bootstrap do Angular. Isso reduz o impacto do cold start.
    this.apiService.warmup().subscribe({ error: () => undefined });
  }
}
