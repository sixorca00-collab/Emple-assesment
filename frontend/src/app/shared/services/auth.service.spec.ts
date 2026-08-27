import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { API_BASE_URL } from '../config/api.config';

// access token de prueba: header.payload.firma con claims conocidos
function fakeJwt(): string {
  const payload = {
    sub: 'user-123',
    is_platform_admin: true,
    name: 'Juan Olarte',
    job_title: 'CTO',
  };
  const body = btoa(JSON.stringify(payload));
  return `header.${body}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('empieza sin sesion', () => {
    expect(service.isLoggedIn()).toBe(false);
    expect(service.currentUser()).toBeNull();
  });

  it('login guarda el access token en memoria y el refresh en localStorage', () => {
    service.login('juan.olarte@riwi.io', 'Password123!').subscribe();

    const req = http.expectOne(`${API_BASE_URL}/auth/login`);
    expect(req.request.body).toEqual({
      email: 'juan.olarte@riwi.io',
      password: 'Password123!',
    });
    req.flush({
      accessToken: fakeJwt(),
      refreshToken: 'refresh-abc',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    });

    expect(service.isLoggedIn()).toBe(true);
    expect(localStorage.getItem('riwi_refresh_token')).toBe('refresh-abc');
  });

  it('register crea la cuenta y deja la sesion abierta (auto-login)', () => {
    service
      .register({
        name: 'Ana Ruiz',
        jobTitle: 'Developer',
        email: 'ana@riwi.io',
        password: 'secret123',
      })
      .subscribe();

    const req = http.expectOne(`${API_BASE_URL}/auth/register`);
    expect(req.request.body).toEqual({
      name: 'Ana Ruiz',
      jobTitle: 'Developer',
      email: 'ana@riwi.io',
      password: 'secret123',
    });
    req.flush({
      accessToken: fakeJwt(),
      refreshToken: 'refresh-abc',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    });

    expect(service.isLoggedIn()).toBe(true);
    expect(localStorage.getItem('riwi_refresh_token')).toBe('refresh-abc');
  });

  it('currentUser expone los claims del token', () => {
    service.login('juan.olarte@riwi.io', 'Password123!').subscribe();
    http.expectOne(`${API_BASE_URL}/auth/login`).flush({
      accessToken: fakeJwt(),
      refreshToken: 'refresh-abc',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    });

    const user = service.currentUser();
    expect(user?.userId).toBe('user-123');
    expect(user?.isPlatformAdmin).toBe(true);
    expect(user?.name).toBe('Juan Olarte');
    expect(user?.jobTitle).toBe('CTO');
  });

  it('logout limpia la sesion y avisa al backend', () => {
    localStorage.setItem('riwi_refresh_token', 'refresh-abc');
    service.accessToken.set(fakeJwt());

    service.logout();

    http.expectOne(`${API_BASE_URL}/auth/logout`).flush(null, { status: 204, statusText: 'No Content' });
    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('riwi_refresh_token')).toBeNull();
  });

  it('ensureSession devuelve false cuando no hay refresh token', (done) => {
    service.ensureSession().subscribe((ok) => {
      expect(ok).toBe(false);
      done();
    });
  });
});
