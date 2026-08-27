import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

// respuesta del backend para POST /auth/login y POST /auth/refresh
interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

// datos del usuario que viajan dentro del access token (claims del JWT)
export interface SessionUser {
  userId: string;
  isPlatformAdmin: boolean;
  name: string;
  jobTitle: string;
}

// clave con la que guardamos el refresh token en localStorage
const REFRESH_TOKEN_KEY = 'riwi_refresh_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  // el access token vive solo en memoria; se pierde al recargar, que es lo mas seguro
  accessToken = signal<string | null>(null);

  // hay sesion mientras tengamos un access token cargado
  isLoggedIn = computed(() => this.accessToken() !== null);

  // datos del usuario derivados del token; null cuando no hay sesion
  currentUser = computed<SessionUser | null>(() => {
    const token = this.accessToken();
    return token ? this.readClaims(token) : null;
  });

  login(email: string, password: string): Observable<void> {
    // pedimos el par de tokens al backend con las credenciales
    return this.http
      .post<TokenResponse>(`${API_BASE_URL}/auth/login`, { email, password })
      .pipe(
        tap((tokens) => this.saveTokens(tokens)),
        map(() => undefined),
      );
  }

  refresh(): Observable<string> {
    const refreshToken = this.getStoredRefreshToken();
    if (!refreshToken) {
      // sin refresh token guardado no hay nada que renovar
      return throwError(() => new Error('no refresh token'));
    }
    // enviamos el refresh token y recibimos un par nuevo (el backend rota el refresh)
    return this.http
      .post<TokenResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken })
      .pipe(
        tap((tokens) => this.saveTokens(tokens)),
        map((tokens) => tokens.accessToken),
      );
  }

  logout(): void {
    const refreshToken = this.getStoredRefreshToken();
    if (refreshToken) {
      // avisamos al backend para que revoque el refresh token; si falla igual limpiamos aqui
      this.http
        .post(`${API_BASE_URL}/auth/logout`, { refreshToken })
        .subscribe({ error: () => {} });
    }
    this.clearSession();
  }

  // lo usa el guard: true si ya hay sesion o si se pudo renovar con el refresh token
  ensureSession(): Observable<boolean> {
    if (this.isLoggedIn()) {
      return of(true);
    }
    if (!this.getStoredRefreshToken()) {
      return of(false);
    }
    return this.refresh().pipe(
      map(() => true),
      catchError(() => of(false)),
    );
  }

  private saveTokens(tokens: TokenResponse): void {
    // el access token en memoria y el refresh en localStorage para sobrevivir recargas
    this.accessToken.set(tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  private clearSession(): void {
    this.accessToken.set(null);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  private getStoredRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  // decodifica el payload del JWT (parte del medio, en base64url) para leer los claims
  private readClaims(token: string): SessionUser | null {
    try {
      const payload = token.split('.')[1];
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      const claims = JSON.parse(json) as Record<string, unknown>;
      return {
        userId: String(claims['sub'] ?? ''),
        isPlatformAdmin: claims['is_platform_admin'] === true,
        name: String(claims['name'] ?? ''),
        jobTitle: String(claims['job_title'] ?? ''),
      };
    } catch {
      // token con formato inesperado: lo tratamos como si no hubiera sesion
      return null;
    }
  }
}
