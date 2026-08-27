import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-copilot',
  standalone: true,
  imports: [TranslatePipe],
  template: `<h2 class="text-xl font-semibold">{{ 'nav.copilot' | translate }}</h2>`,
})
export class CopilotComponent {}
