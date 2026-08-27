import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, TranslatePipe],
  templateUrl: './app.component.html',
})
export class AppComponent {
  constructor(private readonly translate: TranslateService) {}

  switchLang(lang: string): void {
    this.translate.use(lang);
  }
}
