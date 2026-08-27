import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { ProfileComponent } from './profile.component';
import { API_BASE_URL } from '../../shared/config/api.config';

describe('ProfileComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService()
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pasa a listo cuando el perfil carga', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();

    http.expectOne(`${API_BASE_URL}/me`).flush({
      email: 'ana@riwi.io',
      displayName: 'Ana Ruiz',
      jobTitle: 'Dev',
      platformAdmin: true,
      visibleConversationCount: 2
    });

    expect(fixture.componentInstance.status()).toBe('ready');
    expect(fixture.componentInstance.profile()?.displayName).toBe('Ana Ruiz');
  });

  it('marca error si la carga falla', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();

    http.expectOne(`${API_BASE_URL}/me`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(fixture.componentInstance.status()).toBe('error');
  });
});
