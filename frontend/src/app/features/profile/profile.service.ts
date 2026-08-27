import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../shared/config/api.config';
import { MeResponse } from './profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private http = inject(HttpClient);

  getMe(): Observable<MeResponse> {
    // pedimos el perfil del actor; el id sale del JWT que agrega el interceptor
    return this.http.get<MeResponse>(`${API_BASE_URL}/me`);
  }
}
