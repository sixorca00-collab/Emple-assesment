import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../shared/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  // estado de la pantalla: editable, enviando o con error
  status = signal<'idle' | 'loading' | 'error'>('idle');

  // clave i18n del mensaje de error que se muestra bajo el formulario
  errorKey = signal<string>('');

  form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  submit(): void {
    if (this.form.invalid || this.status() === 'loading') {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('loading');
    this.errorKey.set('');
    // bloqueamos el formulario mientras se envia
    this.form.disable();
    const { email, password } = this.form.getRawValue();
    // pedimos el login al servicio de autenticacion
    this.auth.login(email, password).subscribe({
      next: () => this.redirectAfterLogin(),
      error: (err: HttpErrorResponse) => {
        // un 401 son credenciales malas; cualquier otro caso lo tratamos como fallo de red
        this.errorKey.set(
          err.status === 401 ? 'login.error.invalid' : 'login.error.network',
        );
        this.status.set('error');
        // reactivamos el formulario para que el usuario reintente
        this.form.enable();
      },
    });
  }

  private redirectAfterLogin(): void {
    // volvemos a la ruta que el usuario intentaba abrir, o al chat por defecto
    const returnUrl =
      this.route.snapshot.queryParamMap.get('returnUrl') ?? '/chat';
    this.router.navigateByUrl(returnUrl);
  }
}
