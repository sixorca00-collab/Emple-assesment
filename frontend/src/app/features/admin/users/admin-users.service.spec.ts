import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { AdminUsersService } from './admin-users.service';
import { AdminUser } from './admin-users.models';
import { API_BASE_URL } from '../../../shared/config/api.config';

function fakeUser(over: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 'u1',
    displayName: 'Ana Ruiz',
    jobTitle: 'Dev',
    avatarUrl: null,
    isActive: true,
    createdAt: '2026-01-01T00:00:00Z',
    ...over
  };
}

describe('AdminUsersService', () => {
  let service: AdminUsersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AdminUsersService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carga la primera pagina con size y sin cursor', () => {
    service.load('', false);

    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/users`);
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('cursor')).toBeNull();
    req.flush({ items: [fakeUser()], nextCursor: 'c1' });

    expect(service.status()).toBe('ready');
    expect(service.users().length).toBe(1);
    expect(service.hasMore()).toBeTrue();
  });

  it('propaga q e includeInactive como parametros', () => {
    service.load('ana', true);

    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/users`);
    expect(req.request.params.get('q')).toBe('ana');
    expect(req.request.params.get('includeInactive')).toBe('true');
    req.flush({ items: [], nextCursor: null });
  });

  it('loadMore agrega la siguiente pagina usando el cursor', () => {
    service.load('', false);
    http.expectOne((r) => r.url === `${API_BASE_URL}/users`).flush({
      items: [fakeUser({ id: 'u1' })],
      nextCursor: 'c1'
    });

    service.loadMore();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/users`);
    expect(req.request.params.get('cursor')).toBe('c1');
    req.flush({ items: [fakeUser({ id: 'u2' })], nextCursor: null });

    expect(service.users().map((u) => u.id)).toEqual(['u1', 'u2']);
    expect(service.hasMore()).toBeFalse();
  });

  it('marca error si el listado falla', () => {
    service.load('', false);
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/users`)
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(service.status()).toBe('error');
  });

  it('envia PATCH y DELETE a /users/{id}', () => {
    service.updateUser('u1', { displayName: 'Nuevo' }).subscribe();
    const patch = http.expectOne(`${API_BASE_URL}/users/u1`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ displayName: 'Nuevo' });
    patch.flush(fakeUser({ displayName: 'Nuevo' }));

    service.deleteUser('u1').subscribe();
    const del = http.expectOne(`${API_BASE_URL}/users/u1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});
