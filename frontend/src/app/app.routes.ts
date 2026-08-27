import { Routes } from '@angular/router';
import { ChatComponent } from './features/chat/chat.component';
import { CopilotComponent } from './features/copilot/copilot.component';
import { ProfileComponent } from './features/profile/profile.component';

export const routes: Routes = [
  { path: '', redirectTo: 'chat', pathMatch: 'full' },
  { path: 'chat', component: ChatComponent },
  { path: 'copilot', component: CopilotComponent },
  { path: 'profile', component: ProfileComponent },
];
