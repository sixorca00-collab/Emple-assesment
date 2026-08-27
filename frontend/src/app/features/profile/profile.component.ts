import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { ProfileService } from './profile.service';
import { MeResponse } from './profile.models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: 'profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent implements OnInit {
  private profileService = inject(ProfileService);

  // estado de la carga del perfil: sus tres casos async
  status = signal<'loading' | 'error' | 'ready'>('loading');

  // datos del perfil que llegan de GET /me
  profile = signal<MeResponse | null>(null);

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.status.set('loading');
    // pedimos el perfil del actor al backend
    this.profileService.getMe().subscribe({
      next: (data) => {
        this.profile.set(data);
        this.status.set('ready');
      },
      error: () => this.status.set('error')
    });
  }
}
