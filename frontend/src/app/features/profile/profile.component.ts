import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [TranslatePipe],
  template: `<h2 class="text-xl font-semibold">{{ 'nav.profile' | translate }}</h2>`,
})
export class ProfileComponent {}
