import { Injectable, effect, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { API_BASE_URL } from '../../shared/config/api.config';
import { AuthService } from '../../shared/services/auth.service';
import { MessageCreatedEvent, MessageResponse } from './chat.models';

// estado de la conexion en tiempo real para mostrarlo en la cabecera del chat
export type RealtimeStatus = 'connecting' | 'open' | 'closed';

@Injectable({ providedIn: 'root' })
export class ChatRealtimeService {
  private auth = inject(AuthService);

  // cada mensaje nuevo que llega por el socket se emite aca
  readonly incoming$ = new Subject<MessageResponse>();

  // estado visible de la conexion
  readonly status = signal<RealtimeStatus>('closed');

  private socket: WebSocket | null = null;

  // true mientras el chat quiere estar conectado; false tras un disconnect manual
  private wanted = false;

  // token con el que se abrio el socket actual, para detectar renovaciones
  private tokenInUse: string | null = null;

  // cuantos reintentos seguidos llevamos, para el backoff
  private retries = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // si el access token cambia mientras estamos conectados, reabrimos con el nuevo
    effect(() => {
      const token = this.auth.accessToken();
      if (!this.wanted) {
        return;
      }
      if (!token) {
        this.close();
      } else if (token !== this.tokenInUse) {
        this.reopen();
      }
    });
  }

  connect(): void {
    this.wanted = true;
    this.open();
  }

  disconnect(): void {
    // cierre manual: no queremos que se reintente
    this.wanted = false;
    this.clearRetry();
    this.close();
  }

  private open(): void {
    const token = this.auth.accessToken();
    if (!token || this.socket) {
      return;
    }
    this.tokenInUse = token;
    this.status.set('connecting');

    // el backend expone /ws/messages y el token va como query param porque el navegador no permite headers
    const url = API_BASE_URL.replace(/^http/, 'ws') + `/ws/messages?access_token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(url);
    this.socket = ws;

    // conexion lista: reseteamos el contador de reintentos
    ws.onopen = () => {
      this.retries = 0;
      this.status.set('open');
    };

    // llega un frame de texto: parseamos el evento y publicamos el mensaje
    ws.onmessage = (event) => this.handleFrame(event.data);

    // el socket se cerro: si aun lo queremos, reintentamos con backoff
    ws.onclose = () => {
      this.socket = null;
      this.status.set('closed');
      if (this.wanted) {
        this.scheduleRetry();
      }
    };

    // ante un error dejamos que onclose maneje el reintento
    ws.onerror = () => ws.close();
  }

  private handleFrame(raw: string): void {
    try {
      const parsed = JSON.parse(raw) as MessageCreatedEvent;
      if (parsed.type === 'message.created' && parsed.message) {
        this.incoming$.next(parsed.message);
      }
    } catch {
      // frame con formato inesperado: lo ignoramos
    }
  }

  private scheduleRetry(): void {
    this.clearRetry();
    // espera creciente entre 1s y 15s
    const delay = Math.min(1000 * 2 ** this.retries, 15000);
    this.retries += 1;
    this.retryTimer = setTimeout(() => this.open(), delay);
  }

  private clearRetry(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private reopen(): void {
    this.close();
    this.open();
  }

  private close(): void {
    if (this.socket) {
      this.socket.onclose = null;
      this.socket.close();
      this.socket = null;
    }
    this.tokenInUse = null;
    this.status.set('closed');
  }
}
