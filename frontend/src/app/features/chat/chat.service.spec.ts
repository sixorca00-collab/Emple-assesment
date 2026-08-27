import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { ChatService } from './chat.service';
import { API_BASE_URL } from '../../shared/config/api.config';

describe('ChatService', () => {
  let service: ChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista conversaciones con el tamano de pagina', () => {
    service.listConversations(null).subscribe();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/channels`);
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('cursor')).toBeNull();
    req.flush({ items: [], nextCursor: null });
  });

  it('agrega el cursor al pedir mas mensajes', () => {
    service.listMessages('c1', 'abc').subscribe();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/channels/c1/messages`);
    expect(req.request.params.get('cursor')).toBe('abc');
    req.flush({ items: [], nextCursor: null });
  });

  it('publica un mensaje con body y clientNonce', () => {
    service.postMessage('c1', 'hola', 'nonce-1').subscribe();
    const req = http.expectOne(`${API_BASE_URL}/channels/c1/messages`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'hola', clientNonce: 'nonce-1' });
    req.flush({});
  });

  it('busca mensajes pasando q', () => {
    service.search('hola', null, null).subscribe();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/messages/search`);
    expect(req.request.params.get('q')).toBe('hola');
    req.flush({ items: [], nextCursor: null });
  });
});
