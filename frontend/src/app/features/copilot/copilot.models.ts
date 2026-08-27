// estados posibles de una respuesta del copiloto (coinciden con el backend)
export type CopilotStatus = 'answered' | 'refused_no_context' | 'refused_permission' | 'error';

// cita a un mensaje fuente que respalda la respuesta
export interface CopilotCitation {
  messageId: string;
  channelId: string;
  snippet: string;
  rank: number;
}

// consumo de tokens de una consulta
export interface CopilotUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

// respuesta de POST /copilot/query
export interface CopilotQueryResponse {
  answer: string;
  status: CopilotStatus;
  citations: CopilotCitation[];
  usage: CopilotUsage;
}

// entrada del historial persistido (GET /copilot/history)
export interface CopilotHistoryResponse {
  id: string;
  question: string;
  answer: string;
  model: string;
  status: CopilotStatus;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  createdAt: string;
}

// fila del reporte de consumo acumulado (GET /copilot/usage)
export interface CopilotUsageResponse {
  userId: string;
  displayName: string;
  jobTitle: string;
  queryCount: number;
  answeredCount: number;
  refusedCount: number;
  errorCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  lastQueryAt: string | null;
}

// un intercambio pregunta/respuesta que se muestra en pantalla
export interface CopilotTurn {
  question: string;
  answer: string;
  status: CopilotStatus;
  citations: CopilotCitation[];
  usage: CopilotUsage | null;
}
