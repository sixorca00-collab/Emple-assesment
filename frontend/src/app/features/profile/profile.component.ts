import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';

export interface UserProfile {
  fullName: string;
  username: string;
  email: string;
  role: string;
  bio: string;
  statusMessage: string;
  presence: 'online' | 'busy' | 'away' | 'offline';
  notificationsEnabled: boolean;
  emailNotifications: boolean;
  language: string;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: 'profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent {
  // Estado del perfil del usuario (Mock inicial)
  profile = signal<UserProfile>({
    fullName: 'Desarrollador Senior',
    username: 'dev_user',
    email: 'dev.user@empresa.com',
    role: 'Backend / Fullstack Engineer',
    bio: 'Apasionado por las arquitecturas limpias, Angular y Spring Boot.',
    statusMessage: '👨‍💻 Enfocado en sprint actual',
    presence: 'online',
    notificationsEnabled: true,
    emailNotifications: false,
    language: 'es'
  });

  savedSuccessfully = signal<boolean>(false);

  saveProfile() {
    // Simulación de guardado
    this.savedSuccessfully.set(true);
    setTimeout(() => this.savedSuccessfully.set(false), 3000);
  }

  changePresence(status: 'online' | 'busy' | 'away' | 'offline') {
    this.profile.update(p => ({ ...p, presence: status }));
  }
}