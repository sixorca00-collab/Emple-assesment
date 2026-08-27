import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [TranslatePipe],
  template: `<h2 class="text-xl font-semibold">{{ 'nav.chat' | translate }}</h2>`,
})
export class ChatComponent {}
