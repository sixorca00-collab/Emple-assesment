import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { CopilotComponent } from './copilot.component';
import { API_BASE_URL } from '../../shared/config/api.config';

describe('CopilotComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CopilotComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService()
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function createAndSettleInit() {
    const fixture = TestBed.createComponent(CopilotComponent);
    fixture.detectChanges();
    // la carga inicial pide historial y consumo
    http.expectOne((r) => r.url === `${API_BASE_URL}/copilot/history`).flush({ items: [], nextCursor: null });
    http.expectOne(`${API_BASE_URL}/copilot/usage`).flush([]);
    return fixture.componentInstance;
  }

  it('agrega un turno con la respuesta y su estado', () => {
    const cmp = createAndSettleInit();
    cmp.userQuery = 'que dijeron del presupuesto';
    cmp.send();

    http.expectOne(`${API_BASE_URL}/copilot/query`).flush({
      answer: 'No tengo acceso a ese contenido.',
      status: 'refused_permission',
      citations: [],
      usage: { promptTokens: 10, completionTokens: 5, totalTokens: 15 }
    });

    // tras responder recarga historial y consumo
    http.expectOne((r) => r.url === `${API_BASE_URL}/copilot/history`).flush({ items: [], nextCursor: null });
    http.expectOne(`${API_BASE_URL}/copilot/usage`).flush([]);

    expect(cmp.turns().length).toBe(1);
    expect(cmp.turns()[0].status).toBe('refused_permission');
    expect(cmp.isThinking()).toBeFalse();
  });

  it('marca askFailed si la consulta falla', () => {
    const cmp = createAndSettleInit();
    cmp.userQuery = 'hola';
    cmp.send();

    http.expectOne(`${API_BASE_URL}/copilot/query`).flush({}, { status: 500, statusText: 'Server Error' });

    expect(cmp.askFailed()).toBeTrue();
    expect(cmp.isThinking()).toBeFalse();
  });
});
