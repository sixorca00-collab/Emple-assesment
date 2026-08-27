import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminUsersService } from './admin-users.service';
import { AdminUser } from './admin-users.models';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css',
})
export class AdminUsersComponent implements OnInit {
  service = inject(AdminUsersService);

  // texto del buscador y toggle de inactivos
  searchTerm = '';
  includeInactive = false;

  // usuario en edicion (modal abierto) o null
  editing = signal<AdminUser | null>(null);
  // usuario pendiente de confirmar borrado o null
  deleting = signal<AdminUser | null>(null);
  // clave i18n del error de la accion en curso
  actionError = signal<string>('');
  // bloquea los botones del modal mientras se envia
  saving = signal<boolean>(false);

  editForm = new FormGroup({
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    jobTitle: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    bio: new FormControl('', { nonNullable: true }),
    isActive: new FormControl(true, { nonNullable: true }),
  });

  ngOnInit(): void {
    // primera carga sin filtros
    this.service.load('', false);
  }

  applySearch(): void {
    // reinicia el keyset con los filtros actuales
    this.service.load(this.searchTerm, this.includeInactive);
  }

  toggleInactive(): void {
    this.includeInactive = !this.includeInactive;
    this.applySearch();
  }

  openEdit(user: AdminUser): void {
    this.actionError.set('');
    this.editing.set(user);
    // precargamos el formulario con los datos visibles del usuario
    this.editForm.reset({
      displayName: user.displayName,
      jobTitle: user.jobTitle,
      bio: '',
      isActive: user.isActive,
    });
  }

  closeEdit(): void {
    this.editing.set(null);
  }

  saveEdit(): void {
    const user = this.editing();
    if (!user || this.editForm.invalid || this.saving()) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.actionError.set('');
    const value = this.editForm.getRawValue();
    // enviamos bio solo si se escribio algo (null deja el valor actual en el SP)
    const payload = {
      displayName: value.displayName,
      jobTitle: value.jobTitle,
      bio: value.bio.trim() ? value.bio.trim() : undefined,
      isActive: value.isActive,
    };
    // llamamos al PATCH; el SP decide si el actor puede tocar esos campos
    this.service.updateUser(user.id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(null);
        this.service.refresh();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.actionError.set(err.status === 403 ? 'admin.error.forbidden' : 'admin.error.action');
      },
    });
  }

  openDelete(user: AdminUser): void {
    this.actionError.set('');
    this.deleting.set(user);
  }

  closeDelete(): void {
    this.deleting.set(null);
  }

  confirmDelete(): void {
    const user = this.deleting();
    if (!user || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.actionError.set('');
    // llamamos al DELETE (soft delete en el backend)
    this.service.deleteUser(user.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.deleting.set(null);
        this.service.refresh();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.actionError.set(err.status === 403 ? 'admin.error.forbidden' : 'admin.error.action');
      },
    });
  }
}
