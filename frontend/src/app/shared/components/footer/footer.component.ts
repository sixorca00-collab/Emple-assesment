import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './footer.component.html'
})
export class FooterComponent {
  // Año dinámico
  currentYear: number = new Date().getFullYear();
  companyName: string = 'Riwi Messenger';
  developerName: string = 'Juan Olarte / Dev'; }