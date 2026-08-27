import { TestBed } from '@angular/core/testing';
import { ChatRealtimeService } from './chat-realtime.service';
import { AuthService } from '../../shared/services/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

// WebSocket falso: guarda la url y deja disparar los callbacks a mano
class FakeWebSocket {
  static last: FakeWebSocket | null = null;
  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(public url: string) {
    FakeWebSocket.last = this;
  }

  close(): void {
    this.closed = true;
  }
}

describe('ChatRealtimeService', () => {
  let service: ChatRealtimeService;
  let auth: AuthService;
  const realWebSocket = window.WebSocket;

  beforeEach(() => {
    (window as unknown as { WebSocket: unknown }).WebSocket = FakeWebSocket;
    FakeWebSocket.last = null;
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ChatRealtimeService);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    service.disconnect();
    (window as unknown as { WebSocket: unknown }).WebSocket = realWebSocket;
  });

  it('no abre socket si no hay token', () => {
    service.connect();
    expect(FakeWebSocket.last).toBeNull();
  });

  it('abre el socket con el token en el query param', () => {
    auth.accessToken.set('tok-123');
    service.connect();

    expect(FakeWebSocket.last).not.toBeNull();
    expect(FakeWebSocket.last!.url).toContain('ws://localhost:8080/ws/messages?access_token=tok-123');
  });

  it('emite el mensaje cuando llega un evento message.created', () => {
    auth.accessToken.set('tok-123');
    service.connect();

    let received: unknown = null;
    service.incoming$.subscribe((m) => (received = m));

    FakeWebSocket.last!.onmessage!({
      data: JSON.stringify({ type: 'message.created', message: { id: 'm1' } })
    });

    expect(received).toEqual({ id: 'm1' });
  });
});
