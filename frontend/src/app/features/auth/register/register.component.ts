import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../shared/services/auth.service';

// valida a nivel de grupo que password y confirmPassword coincidan
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  return password === confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  // estado de la pantalla: editable, enviando o con error
  status = signal<'idle' | 'loading' | 'error'>('idle');

  // clave i18n del mensaje de error que se muestra bajo el formulario
  errorKey = signal<string>('');

  form = new FormGroup(
    {
      name: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      jobTitle: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      email: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.email],
      }),
      password: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(8)],
      }),
      confirmPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
    },
    { validators: passwordsMatch },
  );

  submit(): void {
    if (this.form.invalid || this.status() === 'loading') {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('loading');
    this.errorKey.set('');
    // bloqueamos el formulario mientras se envia
    this.form.disable();
    const { name, jobTitle, email, password } = this.form.getRawValue();
    // pedimos el registro al servicio de autenticacion (auto-login si sale bien)
    this.auth.register({ name, jobTitle, email, password }).subscribe({
      next: () => this.redirectAfterRegister(),
      error: (err: HttpErrorResponse) => {
        // un 409 es email ya registrado; cualquier otro caso lo tratamos como fallo de red
        this.errorKey.set(
          err.status === 409
            ? 'register.error.emailTaken'
            : 'register.error.network',
        );
        this.status.set('error');
        // reactivamos el formulario para que el usuario reintente
        this.form.enable();
      },
    });
  }

  private redirectAfterRegister(): void {
    // volvemos a la ruta que el usuario intentaba abrir, o al chat por defecto
    const returnUrl =
      this.route.snapshot.queryParamMap.get('returnUrl') ?? '/chat';
    this.router.navigateByUrl(returnUrl);
  }
}
