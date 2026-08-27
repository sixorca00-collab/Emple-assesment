import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { ChatComponent } from './chat.component';
import { ChatRealtimeService } from './chat-realtime.service';
import { AuthService } from '../../shared/services/auth.service';
import { MessageResponse } from './chat.models';
import { API_BASE_URL } from '../../shared/config/api.config';

// doble de prueba del servicio de tiempo real para no abrir un WebSocket real
class RealtimeStub {
  incoming$ = new Subject<MessageResponse>();
  status = signal<'connecting' | 'open' | 'closed'>('closed');
  connect(): void {}
  disconnect(): void {}
}

describe('ChatComponent', () => {
  let http: HttpTestingController;
  let realtime: RealtimeStub;

  beforeEach(async () => {
    realtime = new RealtimeStub();
    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService(),
        { provide: ChatRealtimeService, useValue: realtime }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    spyOn(auth, 'currentUser').and.returnValue({
      userId: 'u1',
      name: 'Me',
      jobTitle: 'Dev',
      isPlatformAdmin: false
    });
  });

  function createReady() {
    const fixture = TestBed.createComponent(ChatComponent);
    fixture.detectChanges();
    // la carga inicial del sidebar no trae canales para no auto-seleccionar
    http.expectOne(`${API_BASE_URL}/channels?size=20`).flush({ items: [], nextCursor: null });
    const cmp = fixture.componentInstance;
    cmp.activeChannelId.set('c1');
    return cmp;
  }

  function serverMessage(over: Partial<MessageResponse>): MessageResponse {
    return {
      id: 'm1',
      channelId: 'c1',
      senderId: 'u1',
      senderName: 'Me',
      body: 'hola',
      status: 'sent',
      createdAt: '2026-08-27T10:00:00Z',
      editedAt: null,
      ...over
    };
  }

  it('envia optimista: primero pendiente y luego enviado', () => {
    const cmp = createReady();
    cmp.newMessage = 'hola';
    cmp.sendMessage();

    expect(cmp.messages()[0].state).toBe('pending');

    http.expectOne(`${API_BASE_URL}/channels/c1/messages`).flush(serverMessage({}));

    expect(cmp.messages()[0].state).toBe('sent');
    expect(cmp.messages()[0].id).toBe('m1');
  });

  it('marca fallido y luego permite reintentar', () => {
    const cmp = createReady();
    cmp.newMessage = 'hola';
    cmp.sendMessage();

    http
      .expectOne(`${API_BASE_URL}/channels/c1/messages`)
      .flush({}, { status: 500, statusText: 'Server Error' });
    expect(cmp.messages()[0].state).toBe('failed');

    cmp.retry(cmp.messages()[0]);
    expect(cmp.messages()[0].state).toBe('pending');

    http.expectOne(`${API_BASE_URL}/channels/c1/messages`).flush(serverMessage({}));
    expect(cmp.messages()[0].state).toBe('sent');
  });

  it('agrega un mensaje entrante de otra persona en el canal abierto', () => {
    const cmp = createReady();
    realtime.incoming$.next(serverMessage({ id: 'm9', senderId: 'u2', senderName: 'Otro', body: 'hey' }));

    // marca leido porque el canal esta abierto
    http.expectOne(`${API_BASE_URL}/channels/c1/read`).flush({ markedRead: 1 });

    expect(cmp.messages().length).toBe(1);
    expect(cmp.messages()[0].senderName).toBe('Otro');
  });
});
