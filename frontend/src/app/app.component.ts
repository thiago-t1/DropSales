import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/services/theme.service';
import { SupportChatComponent } from './shared/support-chat/support-chat.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SupportChatComponent],
  template: `<router-outlet /><app-support-chat />`,
})
export class AppComponent {
  constructor(private readonly themeService: ThemeService) {
    this.themeService.initialize();
  }
}
