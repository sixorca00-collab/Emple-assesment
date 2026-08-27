import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';

interface CopilotMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  codeSnippet?: string;
  codeLanguage?: string;
  timestamp: string;
}

interface PromptSuggestion {
  title: string;
  description: string;
  prompt: string;
  icon: string;
}

@Component({
  selector: 'app-copilot',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: 'copilot.component.html',
  styleUrl: './copilot.component.css'
})
export class CopilotComponent {
  userQuery = '';
  isThinking = signal<boolean>(false);
  isHistoryOpen = signal<boolean>(true);

  // Historial de prompts de la sesión
  recentPrompts = signal<string[]>([
    'Refactorizar servicio de autenticación',
    'Explicar política de CORS en Angular 19',
    'Optimizar consultas de base de datos SQL'
  ]);

  // Sugerencias rápidas
  suggestions: PromptSuggestion[] = [
    {
      title: 'Revisar Código',
      description: 'Analiza tu código en busca de errores o cuellos de botella.',
      prompt: '¿Puedes revisar este fragmento de código y sugerir mejoras de rendimiento?',
      icon: '🔍'
    },
    {
      title: 'Redactar Mensaje',
      description: 'Genera un comunicado técnico para el equipo.',
      prompt: 'Redacta un mensaje para el canal de equipo explicando el nuevo release.',
      icon: '✍️'
    },
    {
      title: 'Explicar Arquitectura',
      description: 'Aprende patrones y buenas prácticas.',
      prompt: 'Explícame la diferencia entre Signals y RxJS Subjects en Angular con ejemplos.',
      icon: '💡'
    }
  ];

  // Conversación actual
  messages = signal<CopilotMessage[]>([
    {
      id: '1',
      sender: 'assistant',
      text: '¡Hola! Soy tu Copiloto. ¿En qué puedo ayudarte hoy con la plataforma o tus proyectos?',
      timestamp: '09:00 AM'
    }
  ]);

  toggleHistory() {
    this.isHistoryOpen.update(v => !v);
  }

  useSuggestion(promptText: string) {
    this.userQuery = promptText;
    this.sendQuery();
  }

  sendQuery() {
    if (!this.userQuery.trim()) return;

    const queryText = this.userQuery;
    const timeNow = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    // 1. Agregar mensaje del usuario
    this.messages.update(msgs => [
      ...msgs,
      {
        id: Date.now().toString(),
        sender: 'user',
        text: queryText,
        timestamp: timeNow
      }
    ]);

    this.userQuery = '';
    this.isThinking.set(true);

    // 2. Simulación de respuesta generativa con delay
    setTimeout(() => {
      this.isThinking.set(false);
      this.messages.update(msgs => [
        ...msgs,
        {
          id: (Date.now() + 1).toString(),
          sender: 'assistant',
          text: `Entendido. He procesado tu solicitud sobre: "${queryText}". Aquí tienes la propuesta estructurada:`,
          codeSnippet: `// Ejemplo de implementación sugerida\nexport class CopilotService {\n  readonly state = signal<string>('Ready');\n\n  execute() {\n    console.log('Procesando solicitud en segundo plano...');\n  }\n}`,
          codeLanguage: 'typescript',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }
      ]);

      // Guardar en el historial lateral
      if (!this.recentPrompts().includes(queryText)) {
        this.recentPrompts.update(prev => [queryText, ...prev.slice(0, 4)]);
      }
    }, 1200);
  }
}