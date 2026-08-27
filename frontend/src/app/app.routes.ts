import { Routes } from '@angular/router';
import { ChatComponent } from './features/chat/chat.component';
import { CopilotComponent } from './features/copilot/copilot.component';
import { ProfileComponent } from './features/profile/profile.component';
import { LoginComponent } from './features/auth/login/login.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { AdminUsersComponent } from './features/admin/users/admin-users.component';
import { authGuard } from './shared/guards/auth.guard';
import { adminGuard } from './shared/guards/admin.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'chat', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  // rutas privadas: el guard exige sesion valida
  { path: 'chat', component: ChatComponent, canActivate: [authGuard] },
  { path: 'copilot', component: CopilotComponent, canActivate: [authGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  // gestion de usuarios: ademas de sesion valida exige rol de administrador de plataforma
  { path: 'admin/users', component: AdminUsersComponent, canActivate: [adminGuard] },
];
