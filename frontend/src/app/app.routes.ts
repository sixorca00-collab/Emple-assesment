import { Routes } from '@angular/router';
import { ChatComponent } from './features/chat/chat.component';
import { CopilotComponent } from './features/copilot/copilot.component';
import { ProfileComponent } from './features/profile/profile.component';
import { LoginComponent } from './features/auth/login.component';
import { authGuard } from './shared/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'chat', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  // rutas privadas: el guard exige sesion valida
  { path: 'chat', component: ChatComponent, canActivate: [authGuard] },
  { path: 'copilot', component: CopilotComponent, canActivate: [authGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
];
