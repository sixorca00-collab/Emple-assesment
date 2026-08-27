import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { AdminUsersComponent } from './admin-users.component';
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

describe('AdminUsersComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUsersComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService()
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function createLoaded(users: AdminUser[], nextCursor: string | null = null) {
    const fixture = TestBed.createComponent(AdminUsersComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url === `${API_BASE_URL}/users`).flush({ items: users, nextCursor });
    fixture.detectChanges();
    return fixture;
  }

  it('lista los usuarios cuando la carga termina', () => {
    const fixture = createLoaded([fakeUser(), fakeUser({ id: 'u2', displayName: 'Luis' })]);
    expect(fixture.componentInstance.service.status()).toBe('ready');
    expect(fixture.nativeElement.textContent).toContain('Ana Ruiz');
    expect(fixture.nativeElement.textContent).toContain('Luis');
  });

  it('reinicia la busqueda con q al enviar el formulario', () => {
    const fixture = createLoaded([fakeUser()]);
    fixture.componentInstance.searchTerm = 'ana';
    fixture.componentInstance.applySearch();

    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/users`);
    expect(req.request.params.get('q')).toBe('ana');
    req.flush({ items: [], nextCursor: null });
  });

  it('edita un usuario y refresca la lista', () => {
    const fixture = createLoaded([fakeUser()]);
    const cmp = fixture.componentInstance;

    cmp.openEdit(fakeUser());
    cmp.editForm.patchValue({ displayName: 'Ana Nueva' });
    cmp.saveEdit();

    const patch = http.expectOne(`${API_BASE_URL}/users/u1`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body.displayName).toBe('Ana Nueva');
    patch.flush(fakeUser({ displayName: 'Ana Nueva' }));

    // tras editar se vuelve a pedir la lista
    http.expectOne((r) => r.url === `${API_BASE_URL}/users`).flush({ items: [], nextCursor: null });
    expect(cmp.editing()).toBeNull();
  });

  it('borra un usuario tras confirmar', () => {
    const fixture = createLoaded([fakeUser()]);
    const cmp = fixture.componentInstance;

    cmp.openDelete(fakeUser());
    cmp.confirmDelete();

    const del = http.expectOne(`${API_BASE_URL}/users/u1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);

    http.expectOne((r) => r.url === `${API_BASE_URL}/users`).flush({ items: [], nextCursor: null });
    expect(cmp.deleting()).toBeNull();
  });

  it('muestra error de permiso si el backend responde 403', () => {
    const fixture = createLoaded([fakeUser()]);
    const cmp = fixture.componentInstance;

    cmp.openEdit(fakeUser());
    cmp.saveEdit();
    http
      .expectOne(`${API_BASE_URL}/users/u1`)
      .flush({}, { status: 403, statusText: 'Forbidden' });

    expect(cmp.actionError()).toBe('admin.error.forbidden');
  });
});
