import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';

interface Message {
  id: string;
  sender: string;
  avatar?: string;
  text: string;
  timestamp: string;
  isMe: boolean;
  status?: 'sent' | 'delivered' | 'read';
}

interface Channel {
  id: string;
  name: string;
  unreadCount?: number;
  icon: string;
  isPrivate?: boolean;
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: 'chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent {
  // Estado para desplegar / colapsar el Sidebar
  isSidebarOpen = signal<boolean>(true);

  // Mensaje en el input de envío
  newMessage = '';

  // Lista de Canales / Conversaciones
  channels: Channel[] = [
    { id: '1', name: 'general', unreadCount: 3, icon: '#' },
    { id: '2', name: 'proyectos-angular', icon: '#' },
    { id: '3', name: 'copiloto-soporte', unreadCount: 1, icon: '🤖', isPrivate: true },
    { id: '4', name: 'desarrolladores', icon: '#' },
  ];

  activeChannel = signal<Channel>(this.channels[0]);

  // Conversación activa
  messages: Message[] = [
    {
      id: 'm1',
      sender: 'Carlos Senior',
      text: '¡Hola a todos! Recuerden revisar las ramas antes del merge.',
      timestamp: '08:45 AM',
      isMe: false
    },
    {
      id: 'm2',
      sender: 'Tú',
      text: 'Entendido. Ya terminamos la maquetación del Shell y los temas.',
      timestamp: '09:10 AM',
      isMe: true,
      status: 'read'
    },
    {
      id: 'm3',
      sender: 'Carlos Senior',
      text: 'Genial. Avancen con la barra plegable de mensajería.',
      timestamp: '09:12 AM',
      isMe: false
    }
  ];

  toggleSidebar() {
    this.isSidebarOpen.update(val => !val);
  }

  selectChannel(channel: Channel) {
    this.activeChannel.set(channel);
    channel.unreadCount = 0; // Limpiar notificaciones al seleccionar
  }

  sendMessage() {
    if (!this.newMessage.trim()) return;

    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    // Agregar mensaje del usuario
    this.messages.push({
      id: Date.now().toString(),
      sender: 'Tú',
      text: this.newMessage,
      timestamp: timeStr,
      isMe: true,
      status: 'sent'
    });

    const sentText = this.newMessage;
    this.newMessage = '';

    // Respuesta autogenerada de prueba (simulación)
    setTimeout(() => {
      this.messages.push({
        id: (Date.now() + 1).toString(),
        sender: 'Bot Sistema',
        text: `Mensaje registrado en #${this.activeChannel().name}: "${sentText}"`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        isMe: false
      });
    }, 700);
  }
}