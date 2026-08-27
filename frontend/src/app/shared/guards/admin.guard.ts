import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

// exige sesion valida y ademas rol de administrador de plataforma; si no, manda al chat
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.ensureSession().pipe(
    map((ok) => {
      // sin sesion valida vamos al login guardando el destino
      if (!ok) {
        return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
      }
      // la autoridad real es el backend; aqui solo evitamos mostrar la vista a un no-admin
      return auth.currentUser()?.isPlatformAdmin ? true : router.createUrlTree(['/chat']);
    }),
  );
};
