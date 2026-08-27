import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { LoginComponent } from './login.component';
import { API_BASE_URL } from '../../shared/config/api.config';

describe('LoginComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
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
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.submit();
    http.expectNone(`${API_BASE_URL}/auth/login`);
    expect(fixture.componentInstance.status()).toBe('idle');
  });

  it('muestra error de credenciales ante un 401', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const cmp = fixture.componentInstance;
    cmp.form.setValue({ email: 'a@b.io', password: 'x' });

    cmp.submit();
    http
      .expectOne(`${API_BASE_URL}/auth/login`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(cmp.status()).toBe('error');
    expect(cmp.errorKey()).toBe('login.error.invalid');
  });

  it('marca error de red ante un fallo de servidor', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const cmp = fixture.componentInstance;
    cmp.form.setValue({ email: 'a@b.io', password: 'x' });

    cmp.submit();
    http
      .expectOne(`${API_BASE_URL}/auth/login`)
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(cmp.errorKey()).toBe('login.error.network');
  });
});
