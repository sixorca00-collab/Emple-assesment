import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ThemeService } from '../../services/theme.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css'
})
export class HeaderComponent {
  public themeService = inject(ThemeService);
  public auth = inject(AuthService);
  private router = inject(Router);

  constructor(private translate: TranslateService) {}

  changeLanguage(lang: string) {
    this.translate.use(lang);
  }

  logout() {
    // cerramos sesion y volvemos al login
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
