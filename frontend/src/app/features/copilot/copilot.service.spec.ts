import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { CopilotService } from './copilot.service';
import { API_BASE_URL } from '../../shared/config/api.config';

describe('CopilotService', () => {
  let service: CopilotService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CopilotService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia la pregunta a POST /copilot/query', () => {
    service.ask('que dijeron del release').subscribe();
    const req = http.expectOne(`${API_BASE_URL}/copilot/query`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ question: 'que dijeron del release' });
    req.flush({ answer: '', status: 'answered', citations: [], usage: { promptTokens: 0, completionTokens: 0, totalTokens: 0 } });
  });

  it('pide el historial con tamano de pagina', () => {
    service.history(null).subscribe();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/copilot/history`);
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ items: [], nextCursor: null });
  });

  it('pide el consumo acumulado', () => {
    service.usage().subscribe();
    const req = http.expectOne(`${API_BASE_URL}/copilot/usage`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
