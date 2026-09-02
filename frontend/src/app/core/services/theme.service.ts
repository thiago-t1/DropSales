import { DOCUMENT } from '@angular/common';
import { Inject, Injectable, computed, signal } from '@angular/core';

export type AppTheme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly storageKey = 'dropsales-theme';
  private readonly mediaQuery =
    typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  private followsSystem = !this.readStoredTheme();

  readonly theme = signal<AppTheme>(this.resolveInitialTheme());
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor(@Inject(DOCUMENT) private readonly document: Document) {
    this.mediaQuery?.addEventListener('change', this.handleSystemThemeChange);
  }

  initialize(): void {
    this.applyTheme(this.theme());
  }

  toggle(): void {
    this.setTheme(this.isDark() ? 'light' : 'dark');
  }

  setTheme(theme: AppTheme): void {
    this.followsSystem = false;
    this.writeStoredTheme(theme);
    this.theme.set(theme);
    this.applyTheme(theme);
  }

  private readonly handleSystemThemeChange = (event: MediaQueryListEvent): void => {
    if (!this.followsSystem) return;
    const theme: AppTheme = event.matches ? 'dark' : 'light';
    this.theme.set(theme);
    this.applyTheme(theme);
  };

  private resolveInitialTheme(): AppTheme {
    return this.readStoredTheme() || (this.mediaQuery?.matches ? 'dark' : 'light');
  }

  private applyTheme(theme: AppTheme): void {
    const root = this.document.documentElement;
    const dark = theme === 'dark';

    root.classList.toggle('dark', dark);
    root.dataset['theme'] = theme;
    root.style.colorScheme = theme;

    const themeColor = this.document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    themeColor?.setAttribute('content', dark ? '#020617' : '#f5f7fb');
  }

  private readStoredTheme(): AppTheme | null {
    if (typeof localStorage === 'undefined') return null;
    try {
      const stored = localStorage.getItem(this.storageKey);
      return stored === 'light' || stored === 'dark' ? stored : null;
    } catch {
      return null;
    }
  }

  private writeStoredTheme(theme: AppTheme): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.setItem(this.storageKey, theme);
    } catch {
      // A preferência continua válida durante a sessão mesmo sem armazenamento.
    }
  }

}
