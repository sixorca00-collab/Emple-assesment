import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { RegisterComponent } from './register.component';
import { API_BASE_URL } from '../../../shared/config/api.config';

// access token minimo valido para que AuthService pueda leer los claims
function fakeJwt(): string {
  const body = btoa(JSON.stringify({ sub: 'user-1', name: 'Ana', job_title: 'Dev' }));
  return `header.${body}.signature`;
}

// deja el formulario en un estado valido para poder enviar
function fillValid(cmp: RegisterComponent): void {
  cmp.form.setValue({
    name: 'Ana Ruiz',
    jobTitle: 'Developer',
    email: 'ana@riwi.io',
    password: 'secret123',
    confirmPassword: 'secret123',
  });
}

describe('RegisterComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService(),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('no envia si el formulario es invalido', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.componentInstance.submit();
    http.expectNone(`${API_BASE_URL}/auth/register`);
    expect(fixture.componentInstance.status()).toBe('idle');
  });

  it('no envia si las contrasenas no coinciden', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const cmp = fixture.componentInstance;
    fillValid(cmp);
    cmp.form.controls.confirmPassword.setValue('otra-cosa');

    cmp.submit();
    http.expectNone(`${API_BASE_URL}/auth/register`);
    expect(cmp.status()).toBe('idle');
    expect(cmp.form.hasError('passwordMismatch')).toBe(true);
  });

  it('muestra error de email en uso ante un 409', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const cmp = fixture.componentInstance;
    fillValid(cmp);

    cmp.submit();
    http
      .expectOne(`${API_BASE_URL}/auth/register`)
      .flush({}, { status: 409, statusText: 'Conflict' });

    expect(cmp.status()).toBe('error');
    expect(cmp.errorKey()).toBe('register.error.emailTaken');
  });

  it('marca error de red ante un fallo de servidor', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const cmp = fixture.componentInstance;
    fillValid(cmp);

    cmp.submit();
    http
      .expectOne(`${API_BASE_URL}/auth/register`)
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(cmp.errorKey()).toBe('register.error.network');
  });

  it('navega al chat tras un registro exitoso', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const cmp = fixture.componentInstance;
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl');
    fillValid(cmp);

    cmp.submit();
    http.expectOne(`${API_BASE_URL}/auth/register`).flush({
      accessToken: fakeJwt(),
      refreshToken: 'refresh-abc',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    });

    expect(navigateSpy).toHaveBeenCalledWith('/chat');
  });
});
