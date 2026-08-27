// una conversacion del sidebar, tal cual la entrega GET /channels
export interface ConversationResponse {
  channelId: string;
  channelName: string;
  isPrivate: boolean;
  myRole: string;
  lastMessageId: string | null;
  lastMessagePreview: string | null;
  lastMessageSenderId: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

// canal recien creado por POST /channels
export interface ChannelResponse {
  id: string;
  name: string;
  description: string;
  isPrivate: boolean;
  myRole: string;
  createdAt: string;
}

// un mensaje individual devuelto por el backend
export interface MessageResponse {
  id: string;
  channelId: string;
  senderId: string;
  senderName: string;
  body: string;
  status: string;
  createdAt: string;
  editedAt: string | null;
}

// resultado de GET /messages/search con el termino resaltado en snippet (<mark>...</mark>)
export interface SearchHitResponse {
  id: string;
  channelId: string;
  channelName: string;
  senderId: string;
  senderName: string;
  createdAt: string;
  snippet: string;
}

// estado visual de un mensaje en pantalla
export type MessageState = 'pending' | 'sent' | 'failed';

// mensaje como lo pinta el chat: los datos del servidor mas lo necesario para el envio optimista
export interface ChatMessage {
  id: string;
  senderId: string;
  senderName: string;
  body: string;
  createdAt: string;
  editedAt: string | null;
  state: MessageState;
  clientNonce: string | null;
}

// evento que llega por el WebSocket cuando alguien publica un mensaje
export interface MessageCreatedEvent {
  type: 'message.created';
  message: MessageResponse;
}
