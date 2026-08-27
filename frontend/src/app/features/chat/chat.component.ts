import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../../shared/services/auth.service';
import { ChatService } from './chat.service';
import { ChatRealtimeService } from './chat-realtime.service';
import {
  ChatMessage,
  ConversationResponse,
  MessageResponse,
  MessageState,
  SearchHitResponse
} from './chat.models';

// los tres estados que puede tener cualquier lista async de esta vista
type AsyncStatus = 'loading' | 'error' | 'ready';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: 'chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent implements OnInit, OnDestroy {
  private chat = inject(ChatService);
  private realtime = inject(ChatRealtimeService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  // contenedor scrolleable de la lista de mensajes
  @ViewChild('messageList') private messageList?: ElementRef<HTMLDivElement>;

  // ----- sidebar de conversaciones -----
  conversations = signal<ConversationResponse[]>([]);
  conversationsStatus = signal<AsyncStatus>('loading');
  private conversationsCursor: string | null = null;
  isSidebarOpen = signal<boolean>(true);

  // ----- panel de mensajes -----
  activeChannelId = signal<string | null>(null);
  messages = signal<ChatMessage[]>([]);
  messagesStatus = signal<AsyncStatus>('loading');
  loadingOlder = signal<boolean>(false);
  private olderCursor: string | null = null;
  newMessage = '';

  // ----- edicion en linea -----
  editingId = signal<string | null>(null);
  editBody = '';

  // ----- crear canal -----
  showCreate = signal<boolean>(false);
  createName = '';
  createDescription = '';
  createPrivate = false;

  // ----- agregar miembro -----
  showAddMember = signal<boolean>(false);
  memberUserId = '';
  memberRole = 'member';

  // ----- buscador -----
  searchQuery = '';
  searchResults = signal<SearchHitResponse[]>([]);
  searchStatus = signal<AsyncStatus | 'idle'>('idle');

  // estado de la conexion en tiempo real
  realtimeStatus = this.realtime.status;

  // id del usuario actual, para saber que mensajes son propios
  myUserId = computed(() => this.auth.currentUser()?.userId ?? null);

  // conversacion abierta ahora mismo
  activeChannel = computed(() =>
    this.conversations().find((c) => c.channelId === this.activeChannelId()) ?? null
  );

  // puede administrar miembros solo si el backend le dio rol owner/admin
  canManageMembers = computed(() => {
    const role = this.activeChannel()?.myRole;
    return role === 'owner' || role === 'admin';
  });

  private incomingSub?: Subscription;

  ngOnInit(): void {
    this.loadConversations();
    // escuchamos los mensajes que llegan por el WebSocket
    this.incomingSub = this.realtime.incoming$.subscribe((msg) => this.applyIncoming(msg));
    // abrimos la conexion en tiempo real
    this.realtime.connect();
  }

  ngOnDestroy(): void {
    this.incomingSub?.unsubscribe();
    // cerramos el socket al salir de la vista
    this.realtime.disconnect();
  }

  toggleSidebar(): void {
    this.isSidebarOpen.update((v) => !v);
  }

  // ----- conversaciones -----

  loadConversations(): void {
    this.conversationsStatus.set('loading');
    // primera pagina del sidebar
    this.chat.listConversations(null).subscribe({
      next: (page) => {
        this.conversations.set(page.items);
        this.conversationsCursor = page.nextCursor;
        this.conversationsStatus.set('ready');
        this.pickInitialChannel();
      },
      error: () => this.conversationsStatus.set('error')
    });
  }

  loadMoreConversations(): void {
    if (!this.conversationsCursor) {
      return;
    }
    // siguiente pagina del sidebar con el cursor keyset
    this.chat.listConversations(this.conversationsCursor).subscribe({
      next: (page) => {
        this.conversations.set([...this.conversations(), ...page.items]);
        this.conversationsCursor = page.nextCursor;
      },
      error: () => {}
    });
  }

  get hasMoreConversations(): boolean {
    return this.conversationsCursor !== null;
  }

  private pickInitialChannel(): void {
    if (this.activeChannelId()) {
      return;
    }
    // una cita del copiloto puede pedir abrir un canal concreto
    const wanted = this.route.snapshot.queryParamMap.get('channelId');
    const exists = this.conversations().some((c) => c.channelId === wanted);
    const target = exists ? wanted : this.conversations()[0]?.channelId ?? null;
    if (target) {
      this.selectChannel(target);
    }
  }

  // ----- mensajes -----

  selectChannel(channelId: string): void {
    this.activeChannelId.set(channelId);
    this.messages.set([]);
    this.olderCursor = null;
    this.editingId.set(null);
    this.searchStatus.set('idle');
    this.searchResults.set([]);
    this.loadMessages(channelId);
    this.markReadQuietly();
  }

  loadMessages(channelId: string): void {
    this.messagesStatus.set('loading');
    // primera pagina del historial (viene DESC, la invertimos a ascendente)
    this.chat.listMessages(channelId, null).subscribe({
      next: (page) => {
        this.messages.set([...page.items].reverse().map((m) => this.toChatMessage(m)));
        this.olderCursor = page.nextCursor;
        this.messagesStatus.set('ready');
        this.scrollToBottomSoon();
      },
      error: () => this.messagesStatus.set('error')
    });
  }

  onMessagesScroll(): void {
    const el = this.messageList?.nativeElement;
    if (!el) {
      return;
    }
    // cerca del tope pedimos los mensajes mas antiguos
    if (el.scrollTop < 60) {
      this.loadOlder();
    }
  }

  loadOlder(): void {
    const channelId = this.activeChannelId();
    if (!channelId || !this.olderCursor || this.loadingOlder()) {
      return;
    }
    this.loadingOlder.set(true);
    const el = this.messageList?.nativeElement;
    const previousHeight = el ? el.scrollHeight : 0;
    // pagina siguiente de mensajes viejos con el cursor keyset
    this.chat.listMessages(channelId, this.olderCursor).subscribe({
      next: (page) => {
        const older = [...page.items].reverse().map((m) => this.toChatMessage(m));
        this.messages.set([...older, ...this.messages()]);
        this.olderCursor = page.nextCursor;
        this.loadingOlder.set(false);
        // conservamos la posicion de lectura tras anteponer los mensajes viejos
        setTimeout(() => {
          if (el) {
            el.scrollTop = el.scrollHeight - previousHeight;
          }
        });
      },
      error: () => this.loadingOlder.set(false)
    });
  }

  sendMessage(): void {
    const channelId = this.activeChannelId();
    const body = this.newMessage.trim();
    if (!channelId || !body) {
      return;
    }
    const clientNonce = crypto.randomUUID();
    // pintamos el mensaje como pendiente de inmediato (envio optimista)
    const optimistic: ChatMessage = {
      id: clientNonce,
      senderId: this.myUserId() ?? '',
      senderName: this.auth.currentUser()?.name ?? '',
      body,
      createdAt: new Date().toISOString(),
      editedAt: null,
      state: 'pending',
      clientNonce
    };
    this.messages.set([...this.messages(), optimistic]);
    this.newMessage = '';
    this.scrollToBottomSoon();
    this.postMessage(channelId, body, clientNonce);
  }

  retry(message: ChatMessage): void {
    const channelId = this.activeChannelId();
    if (!channelId || !message.clientNonce) {
      return;
    }
    // volvemos a pendiente y reintentamos con el mismo nonce para no duplicar
    this.patchMessageState(message.clientNonce, 'pending');
    this.postMessage(channelId, message.body, message.clientNonce);
  }

  private postMessage(channelId: string, body: string, clientNonce: string): void {
    // publicamos el mensaje en el backend
    this.chat.postMessage(channelId, body, clientNonce).subscribe({
      next: (saved) => this.onSendSuccess(clientNonce, saved),
      error: () => this.patchMessageState(clientNonce, 'failed')
    });
  }

  private onSendSuccess(clientNonce: string, saved: MessageResponse): void {
    const list = this.messages();
    const idx = list.findIndex((m) => m.clientNonce === clientNonce);
    if (idx === -1) {
      return;
    }
    const copy = [...list];
    const alreadyByWebSocket = list.some((m) => m.id === saved.id && m.clientNonce === null);
    if (alreadyByWebSocket) {
      // el evento en tiempo real ya agrego el mensaje real: quitamos el optimista
      copy.splice(idx, 1);
    } else {
      copy[idx] = this.toChatMessage(saved);
    }
    this.messages.set(copy);
  }

  // ----- edicion y borrado -----

  startEdit(message: ChatMessage): void {
    this.editingId.set(message.id);
    this.editBody = message.body;
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editBody = '';
  }

  saveEdit(message: ChatMessage): void {
    const body = this.editBody.trim();
    if (!body || body === message.body) {
      this.cancelEdit();
      return;
    }
    // pedimos la edicion; el backend solo la permite al autor
    this.chat.editMessage(message.id, body).subscribe({
      next: (updated) => {
        this.replaceMessage(updated);
        this.cancelEdit();
      },
      error: () => this.cancelEdit()
    });
  }

  deleteMessage(message: ChatMessage): void {
    // borrado logico en el backend; aca lo quitamos de la lista
    this.chat.deleteMessage(message.id).subscribe({
      next: () => this.messages.set(this.messages().filter((m) => m.id !== message.id)),
      error: () => {}
    });
  }

  canEdit(message: ChatMessage): boolean {
    return message.senderId === this.myUserId() && message.state === 'sent';
  }

  canDelete(message: ChatMessage): boolean {
    const own = message.senderId === this.myUserId();
    return (own || this.canManageMembers() || this.auth.currentUser()?.isPlatformAdmin === true)
      && message.state === 'sent';
  }

  // ----- crear canal -----

  submitCreateChannel(): void {
    const name = this.createName.trim();
    if (!name) {
      return;
    }
    // creamos el canal y lo abrimos al terminar
    this.chat.createChannel(name, this.createDescription.trim(), this.createPrivate).subscribe({
      next: (channel) => {
        this.showCreate.set(false);
        this.createName = '';
        this.createDescription = '';
        this.createPrivate = false;
        this.loadConversations();
        this.activeChannelId.set(channel.id);
        this.selectChannel(channel.id);
      },
      error: () => {}
    });
  }

  // ----- agregar miembro -----

  submitAddMember(): void {
    const channelId = this.activeChannelId();
    const userId = this.memberUserId.trim();
    if (!channelId || !userId) {
      return;
    }
    // el backend valida que el actor sea owner/admin del canal
    this.chat.addMember(channelId, userId, this.memberRole).subscribe({
      next: () => {
        this.showAddMember.set(false);
        this.memberUserId = '';
        this.memberRole = 'member';
      },
      error: () => {}
    });
  }

  // ----- buscador -----

  runSearch(): void {
    const query = this.searchQuery.trim();
    if (!query) {
      this.searchStatus.set('idle');
      this.searchResults.set([]);
      return;
    }
    this.searchStatus.set('loading');
    // buscamos en todos los canales del actor; la RLS filtra el resto
    this.chat.search(query, null, null).subscribe({
      next: (page) => {
        this.searchResults.set(page.items);
        this.searchStatus.set('ready');
      },
      error: () => this.searchStatus.set('error')
    });
  }

  openHit(hit: SearchHitResponse): void {
    this.searchQuery = '';
    this.searchStatus.set('idle');
    this.searchResults.set([]);
    this.selectChannel(hit.channelId);
  }

  // ----- tiempo real -----

  private applyIncoming(msg: MessageResponse): void {
    if (msg.channelId !== this.activeChannelId()) {
      // evento de otro canal: solo refrescamos su fila en el sidebar
      this.bumpConversation(msg);
      return;
    }
    const list = this.messages();
    const byId = list.findIndex((m) => m.id === msg.id);
    if (byId !== -1) {
      // ya lo teniamos: actualizamos cuerpo y estado
      const copy = [...list];
      copy[byId] = { ...copy[byId], body: msg.body, editedAt: msg.editedAt, state: this.toState(msg.status) };
      this.messages.set(copy);
      return;
    }
    const optimistic = list.findIndex(
      (m) => m.clientNonce !== null && m.senderId === this.myUserId() && m.body === msg.body
    );
    if (optimistic !== -1) {
      // es el eco de un mensaje propio que enviamos de forma optimista
      const copy = [...list];
      copy[optimistic] = this.toChatMessage(msg);
      this.messages.set(copy);
      return;
    }
    // mensaje nuevo de otra persona en el canal abierto
    this.messages.set([...list, this.toChatMessage(msg)]);
    this.scrollToBottomSoon();
    this.markReadQuietly();
  }

  private bumpConversation(msg: MessageResponse): void {
    this.conversations.set(
      this.conversations().map((c) =>
        c.channelId === msg.channelId
          ? {
              ...c,
              lastMessagePreview: msg.body,
              lastMessageAt: msg.createdAt,
              lastMessageSenderId: msg.senderId,
              lastMessageId: msg.id,
              unreadCount: c.unreadCount + 1
            }
          : c
      )
    );
  }

  private markReadQuietly(): void {
    const channelId = this.activeChannelId();
    if (!channelId) {
      return;
    }
    // marcamos el canal como leido y ponemos su contador en cero
    this.chat.markRead(channelId).subscribe({
      next: () => {
        this.conversations.set(
          this.conversations().map((c) =>
            c.channelId === channelId ? { ...c, unreadCount: 0 } : c
          )
        );
      },
      error: () => {}
    });
  }

  // ----- helpers -----

  private toChatMessage(m: MessageResponse): ChatMessage {
    return {
      id: m.id,
      senderId: m.senderId,
      senderName: m.senderName,
      body: m.body,
      createdAt: m.createdAt,
      editedAt: m.editedAt,
      state: this.toState(m.status),
      clientNonce: null
    };
  }

  private toState(status: string): MessageState {
    return status === 'pending' || status === 'failed' ? status : 'sent';
  }

  private replaceMessage(updated: MessageResponse): void {
    this.messages.set(
      this.messages().map((m) => (m.id === updated.id ? this.toChatMessage(updated) : m))
    );
  }

  private patchMessageState(clientNonce: string, state: MessageState): void {
    this.messages.set(
      this.messages().map((m) => (m.clientNonce === clientNonce ? { ...m, state } : m))
    );
  }

  private scrollToBottomSoon(): void {
    setTimeout(() => {
      const el = this.messageList?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  }
}
