import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { CopilotService } from './copilot.service';
import {
  CopilotHistoryResponse,
  CopilotTurn,
  CopilotUsageResponse
} from './copilot.models';

// tres estados para las listas async de esta vista
type AsyncStatus = 'loading' | 'error' | 'ready';

@Component({
  selector: 'app-copilot',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, RouterLink],
  templateUrl: 'copilot.component.html',
  styleUrl: './copilot.component.css'
})
export class CopilotComponent implements OnInit {
  private copilot = inject(CopilotService);

  userQuery = '';

  // true mientras esperamos la respuesta del copiloto
  isThinking = signal<boolean>(false);

  // se pone en true si la ultima consulta fallo por red
  askFailed = signal<boolean>(false);

  // intercambios pregunta/respuesta que se muestran en el area principal
  turns = signal<CopilotTurn[]>([]);

  // ----- historial persistido -----
  isHistoryOpen = signal<boolean>(true);
  history = signal<CopilotHistoryResponse[]>([]);
  historyStatus = signal<AsyncStatus>('loading');
  private historyCursor: string | null = null;

  // ----- consumo acumulado -----
  usage = signal<CopilotUsageResponse | null>(null);
  usageStatus = signal<AsyncStatus>('loading');

  ngOnInit(): void {
    this.loadHistory();
    this.loadUsage();
  }

  toggleHistory(): void {
    this.isHistoryOpen.update((v) => !v);
  }

  send(): void {
    const question = this.userQuery.trim();
    if (!question || this.isThinking()) {
      return;
    }
    this.userQuery = '';
    this.askFailed.set(false);
    this.isThinking.set(true);
    // preguntamos al copiloto
    this.copilot.ask(question).subscribe({
      next: (res) => {
        this.turns.update((list) => [
          ...list,
          {
            question,
            answer: res.answer,
            status: res.status,
            citations: res.citations,
            usage: res.usage
          }
        ]);
        this.isThinking.set(false);
        this.refreshAfterAsk();
      },
      error: () => {
        this.isThinking.set(false);
        this.askFailed.set(true);
        this.userQuery = question;
      }
    });
  }

  private refreshAfterAsk(): void {
    // tras una consulta nueva volvemos a cargar historial y consumo
    this.historyCursor = null;
    this.loadHistory();
    this.loadUsage();
  }

  // ----- historial -----

  loadHistory(): void {
    this.historyStatus.set('loading');
    // primera pagina del historial del actor
    this.copilot.history(null).subscribe({
      next: (page) => {
        this.history.set(page.items);
        this.historyCursor = page.nextCursor;
        this.historyStatus.set('ready');
      },
      error: () => this.historyStatus.set('error')
    });
  }

  loadMoreHistory(): void {
    if (!this.historyCursor) {
      return;
    }
    // siguiente pagina del historial con el cursor keyset
    this.copilot.history(this.historyCursor).subscribe({
      next: (page) => {
        this.history.set([...this.history(), ...page.items]);
        this.historyCursor = page.nextCursor;
      },
      error: () => {}
    });
  }

  get hasMoreHistory(): boolean {
    return this.historyCursor !== null;
  }

  openFromHistory(entry: CopilotHistoryResponse): void {
    // mostramos una entrada guardada como un intercambio mas en pantalla
    this.turns.update((list) => [
      ...list,
      {
        question: entry.question,
        answer: entry.answer,
        status: entry.status,
        citations: [],
        usage: {
          promptTokens: entry.promptTokens,
          completionTokens: entry.completionTokens,
          totalTokens: entry.totalTokens
        }
      }
    ]);
  }

  // ----- consumo -----

  loadUsage(): void {
    this.usageStatus.set('loading');
    // traemos el consumo acumulado del actor
    this.copilot.usage().subscribe({
      next: (rows) => {
        this.usage.set(rows[0] ?? null);
        this.usageStatus.set('ready');
      },
      error: () => this.usageStatus.set('error')
    });
  }
}
