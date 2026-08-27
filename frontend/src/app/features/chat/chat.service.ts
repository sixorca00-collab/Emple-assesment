import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../shared/config/api.config';
import { PageResponse } from '../../shared/models/page-response.model';
import {
  ChannelResponse,
  ConversationResponse,
  MessageResponse,
  SearchHitResponse
} from './chat.models';

// tamano de pagina que pedimos en todos los listados del chat
const PAGE_SIZE = 20;

@Injectable({ providedIn: 'root' })
export class ChatService {
  private http = inject(HttpClient);

  listConversations(cursor: string | null): Observable<PageResponse<ConversationResponse>> {
    let params = new HttpParams().set('size', PAGE_SIZE);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    // traemos el listado del sidebar; el backend ya filtra por membresia
    return this.http.get<PageResponse<ConversationResponse>>(`${API_BASE_URL}/channels`, { params });
  }

  listMessages(channelId: string, cursor: string | null): Observable<PageResponse<MessageResponse>> {
    let params = new HttpParams().set('size', PAGE_SIZE);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    // historial del canal, viene DESC (mas nuevo primero); en pantalla lo invertimos
    return this.http.get<PageResponse<MessageResponse>>(
      `${API_BASE_URL}/channels/${channelId}/messages`, { params });
  }

  postMessage(channelId: string, body: string, clientNonce: string): Observable<MessageResponse> {
    // publicamos el mensaje; el clientNonce sirve para que un reintento no lo duplique
    return this.http.post<MessageResponse>(
      `${API_BASE_URL}/channels/${channelId}/messages`, { body, clientNonce });
  }

  editMessage(messageId: string, body: string): Observable<MessageResponse> {
    // el backend solo deja editar al autor del mensaje
    return this.http.patch<MessageResponse>(`${API_BASE_URL}/messages/${messageId}`, { body });
  }

  deleteMessage(messageId: string): Observable<void> {
    // borrado logico: el backend nunca elimina la fila
    return this.http.delete<void>(`${API_BASE_URL}/messages/${messageId}`);
  }

  markRead(channelId: string): Observable<{ markedRead: number }> {
    // avisamos que el actor ya vio el canal para poner el contador de no leidos en cero
    return this.http.post<{ markedRead: number }>(`${API_BASE_URL}/channels/${channelId}/read`, {});
  }

  createChannel(name: string, description: string, isPrivate: boolean): Observable<ChannelResponse> {
    // creamos el canal; la BD deja al actor como owner
    return this.http.post<ChannelResponse>(`${API_BASE_URL}/channels`,
      { name, description, isPrivate });
  }

  addMember(channelId: string, userId: string, role: string): Observable<void> {
    // agregar miembros solo lo permite el backend a owner/admin del canal
    return this.http.post<void>(`${API_BASE_URL}/channels/${channelId}/members`, { userId, role });
  }

  search(query: string, channelId: string | null, cursor: string | null): Observable<PageResponse<SearchHitResponse>> {
    let params = new HttpParams().set('q', query).set('size', PAGE_SIZE);
    if (channelId) {
      params = params.set('channelId', channelId);
    }
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    // busqueda full-text; la RLS limita los resultados a canales del actor
    return this.http.get<PageResponse<SearchHitResponse>>(`${API_BASE_URL}/messages/search`, { params });
  }
}
