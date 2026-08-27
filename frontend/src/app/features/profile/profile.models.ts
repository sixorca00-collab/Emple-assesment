// respuesta de GET /me: perfil del usuario autenticado, tal cual lo entrega el backend
export interface MeResponse {
  email: string;
  displayName: string;
  jobTitle: string;
  platformAdmin: boolean;
  visibleConversationCount: number;
}
