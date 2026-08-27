import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

// agrega la cabecera Authorization si tenemos token
function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token) {
    return req;
  }
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // las rutas de login/refresh/logout no llevan token
  if (req.url.includes('/auth/')) {
    return next(req);
  }

  return next(withToken(req, auth.accessToken())).pipe(
    catchError((error: HttpErrorResponse) => {
      // solo reintentamos ante un 401 y solo una vez
      if (error.status !== 401) {
        return throwError(() => error);
      }
      // pedimos un token nuevo y reintentamos la peticion original
      return auth.refresh().pipe(
        switchMap((newToken) => next(withToken(req, newToken))),
        catchError((refreshError) => {
          // si el refresh falla cerramos sesion y mandamos al login
          auth.logout();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
