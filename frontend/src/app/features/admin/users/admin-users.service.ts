import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from '../../../shared/config/api.config';
import { PageResponse } from '../../../shared/models/page-response.model';
import { AdminUser, UpdateUserPayload } from './admin-users.models';

// tamano de pagina keyset que pedimos al listado de usuarios
const PAGE_SIZE = 20;

@Injectable({ providedIn: 'root' })
export class AdminUsersService {
  private http = inject(HttpClient);

  // estado de la carga del listado: sus tres casos async
  status = signal<'loading' | 'error' | 'ready'>('loading');

  // usuarios acumulados de todas las paginas traidas
  users = signal<AdminUser[]>([]);

  // cursor opaco de la siguiente pagina; null cuando ya no hay mas
  private nextCursor = signal<string | null>(null);

  // filtros vigentes de la busqueda
  private query = signal<string>('');
  private includeInactive = signal<boolean>(false);

  // hay mas paginas mientras el backend siga devolviendo cursor
  hasMore = computed(() => this.nextCursor() !== null);

  // primera pagina: reinicia acumulado y filtros
  load(query: string, includeInactive: boolean): void {
    this.query.set(query.trim());
    this.includeInactive.set(includeInactive);
    this.nextCursor.set(null);
    this.users.set([]);
    this.status.set('loading');
    this.fetch();
  }

  // pagina siguiente por keyset; conserva lo ya cargado
  loadMore(): void {
    if (!this.hasMore()) {
      return;
    }
    this.fetch();
  }

  // recarga con los filtros actuales (tras editar o eliminar)
  refresh(): void {
    this.load(this.query(), this.includeInactive());
  }

  updateUser(id: string, payload: UpdateUserPayload) {
    // el backend (SP) valida permisos; aqui solo enviamos los campos cambiados
    return this.http.patch<AdminUser>(`${API_BASE_URL}/users/${id}`, payload);
  }

  deleteUser(id: string) {
    // soft delete: el backend nunca elimina la fila
    return this.http.delete<void>(`${API_BASE_URL}/users/${id}`);
  }

  private fetch(): void {
    let params = new HttpParams().set('size', PAGE_SIZE);
    if (this.query()) {
      params = params.set('q', this.query());
    }
    if (this.includeInactive()) {
      params = params.set('includeInactive', true);
    }
    const cursor = this.nextCursor();
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    // pedimos la pagina al backend; el SP ya restringe filas y campos del no-admin
    this.http
      .get<PageResponse<AdminUser>>(`${API_BASE_URL}/users`, { params })
      .subscribe({
        next: (page) => {
          this.users.update((current) => [...current, ...page.items]);
          this.nextCursor.set(page.nextCursor);
          this.status.set('ready');
        },
        error: () => this.status.set('error'),
      });
  }
}
