import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../shared/config/api.config';
import { PageResponse } from '../../shared/models/page-response.model';
import {
  CopilotHistoryResponse,
  CopilotQueryResponse,
  CopilotUsageResponse
} from './copilot.models';

// tamano de pagina del historial del copiloto
const PAGE_SIZE = 10;

@Injectable({ providedIn: 'root' })
export class CopilotService {
  private http = inject(HttpClient);

  ask(question: string): Observable<CopilotQueryResponse> {
    // enviamos la pregunta; el actor y su contexto salen del JWT en el backend
    return this.http.post<CopilotQueryResponse>(`${API_BASE_URL}/copilot/query`, { question });
  }

  history(cursor: string | null): Observable<PageResponse<CopilotHistoryResponse>> {
    let params = new HttpParams().set('size', PAGE_SIZE);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    // historial de consultas del propio actor con paginacion keyset
    return this.http.get<PageResponse<CopilotHistoryResponse>>(
      `${API_BASE_URL}/copilot/history`, { params });
  }

  usage(): Observable<CopilotUsageResponse[]> {
    // consumo acumulado del copiloto (Consulta 4); sin userId el backend devuelve lo del actor
    return this.http.get<CopilotUsageResponse[]>(`${API_BASE_URL}/copilot/usage`);
  }
}
