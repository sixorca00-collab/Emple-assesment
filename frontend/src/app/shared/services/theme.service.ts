import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  // Manejamos el estado reactivo con Signals de Angular
  isDarkMode = signal<boolean>(false);

  constructor() {
    // Al iniciar, verifica si había una preferencia guardada o la del sistema
    const savedTheme = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    
    if (savedTheme === 'dark' || (!savedTheme && prefersDark)) {
      this.enableDark();
    } else {
      this.disableDark();
    }
  }

  toggleTheme() {
    if (this.isDarkMode()) {
      this.disableDark();
    } else {
      this.enableDark();
    }
  }

  private enableDark() {
    this.isDarkMode.set(true);
    document.documentElement.classList.add('dark');
    localStorage.setItem('theme', 'dark');
  }

  private disableDark() {
    this.isDarkMode.set(false);
    document.documentElement.classList.remove('dark');
    localStorage.setItem('theme', 'light');
  }
}