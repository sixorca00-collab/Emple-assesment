import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { ProfileService } from './profile.service';
import { API_BASE_URL } from '../../shared/config/api.config';

describe('ProfileService', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide el perfil del actor a GET /me', () => {
    const fake = {
      email: 'ana@riwi.io',
      displayName: 'Ana Ruiz',
      jobTitle: 'Dev',
      platformAdmin: false,
      visibleConversationCount: 3
    };
    let received: unknown;
    service.getMe().subscribe((data) => (received = data));

    const req = http.expectOne(`${API_BASE_URL}/me`);
    expect(req.request.method).toBe('GET');
    req.flush(fake);

    expect(received).toEqual(fake);
  });
});
