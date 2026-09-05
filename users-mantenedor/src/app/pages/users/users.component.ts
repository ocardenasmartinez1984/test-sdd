import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { UserService } from '../../services/user.service';
import { User, CreateUserRequest, UpdateUserRequest } from '../../models/user.model';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>Gestión de Usuarios</h2>
        <button class="btn btn-primary" (click)="openCreateForm()">
          <span class="material-icons">person_add</span>
          Nuevo Usuario
        </button>
      </div>

      @if (successMessage()) {
        <div class="alert alert-success">{{ successMessage() }}</div>
      }
      @if (errorMessage()) {
        <div class="alert alert-error">{{ errorMessage() }}</div>
      }

      <div class="card">
        @if (loading()) {
          <div class="loading-state">
            <div class="spinner-large"></div>
            <p>Cargando usuarios...</p>
          </div>
        } @else {
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Nombre Completo</th>
                  <th>Estado</th>
                  <th>Roles</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (user of users(); track user.id) {
                  <tr>
                    <td><code>{{ user.username }}</code></td>
                    <td>{{ user.email }}</td>
                    <td>{{ user.fullName }}</td>
                    <td>
                      <span class="badge" [class.badge-success]="user.enabled" [class.badge-danger]="!user.enabled">
                        {{ user.enabled ? 'Activo' : 'Inactivo' }}
                      </span>
                    </td>
                    <td>
                      @for (role of user.roles; track role) {
                        <span class="badge badge-info role-badge">{{ role }}</span>
                      }
                    </td>
                    <td class="actions-cell">
                      <button class="btn btn-sm btn-secondary" (click)="openEditForm(user)" title="Editar">
                        <span class="material-icons">edit</span>
                      </button>
                      <button class="btn btn-sm btn-danger" (click)="confirmDelete(user)" title="Eliminar">
                        <span class="material-icons">delete</span>
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="6" class="empty-state">No hay usuarios registrados</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

      <!-- Modal Form -->
      @if (showForm()) {
        <div class="modal-overlay" (click)="closeForm()">
          <div class="modal-content" (click)="$event.stopPropagation()">
            <h3>{{ editingUser() ? 'Editar Usuario' : 'Nuevo Usuario' }}</h3>
            <form [formGroup]="userForm" (ngSubmit)="onSubmit()">
              <div class="form-group">
                <label>Username</label>
                <input formControlName="username" placeholder="Nombre de usuario"
                  [attr.readonly]="editingUser() ? true : null">
              </div>
              <div class="form-group">
                <label>Email</label>
                <input type="email" formControlName="email" placeholder="correo@ejemplo.com">
              </div>
              <div class="form-group">
                <label>{{ editingUser() ? 'Nueva Contraseña (dejar vacío para no cambiar)' : 'Contraseña' }}</label>
                <input type="password" formControlName="password" placeholder="Contraseña">
              </div>
              <div class="form-group">
                <label>{{ editingUser() ? 'Confirmar Nueva Contraseña' : 'Confirmar Contraseña' }}</label>
                <input type="password" formControlName="confirmPassword" placeholder="Repite la contraseña">
                @if (userForm.errors?.['passwordMismatch'] && userForm.get('confirmPassword')?.touched) {
                  <span class="field-error">Las contraseñas no coinciden</span>
                }
              </div>
              <div class="form-group">
                <label>Nombre Completo</label>
                <input formControlName="fullName" placeholder="Nombre completo del usuario">
              </div>

              @if (editingUser()) {
                <div class="form-group toggle-group">
                  <label>Estado</label>
                  <div class="toggle-wrapper">
                    <label class="toggle">
                      <input type="checkbox" formControlName="enabled">
                      <span class="toggle-slider"></span>
                    </label>
                    <span class="toggle-label">{{ userForm.get('enabled')?.value ? 'Activo' : 'Inactivo' }}</span>
                  </div>
                </div>
              }

              <div class="form-group">
                <label>Roles</label>
                <div class="checkbox-group">
                  <label class="checkbox-label">
                    <input type="checkbox" [checked]="hasRole('ROLE_ADMIN')" (change)="toggleRole('ROLE_ADMIN')">
                    <span class="checkmark"></span>
                    ROLE_ADMIN
                  </label>
                  <label class="checkbox-label">
                    <input type="checkbox" [checked]="hasRole('ROLE_USER')" (change)="toggleRole('ROLE_USER')">
                    <span class="checkmark"></span>
                    ROLE_USER
                  </label>
                </div>
              </div>

              <div class="modal-actions">
                <button type="button" class="btn btn-secondary" (click)="closeForm()">Cancelar</button>
                <button type="submit" class="btn btn-primary" [disabled]="submitting() || userForm.invalid">
                  @if (submitting()) {
                    <span class="spinner"></span> Guardando...
                  } @else {
                    {{ editingUser() ? 'Actualizar' : 'Crear' }}
                  }
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Delete Confirmation Modal -->
      @if (showDeleteConfirm()) {
        <div class="modal-overlay" (click)="cancelDelete()">
          <div class="modal-content modal-sm" (click)="$event.stopPropagation()">
            <h3>Confirmar Eliminación</h3>
            <p class="confirm-text">¿Estás seguro de que deseas eliminar al usuario <strong>{{ deletingUser()?.username }}</strong>?</p>
            <p class="confirm-warning">Esta acción no se puede deshacer.</p>
            <div class="modal-actions">
              <button class="btn btn-secondary" (click)="cancelDelete()">Cancelar</button>
              <button class="btn btn-danger" (click)="onDelete()" [disabled]="submitting()">
                @if (submitting()) {
                  <span class="spinner"></span> Eliminando...
                } @else {
                  Eliminar
                }
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .table-wrapper {
      overflow-x: auto;
    }

    .actions-cell {
      display: flex;
      gap: 8px;
    }

    .btn-sm {
      padding: 8px 12px;
      font-size: 12px;
    }

    .btn-sm .material-icons {
      font-size: 16px;
    }

    .role-badge {
      margin-right: 4px;
      font-size: 10px;
    }

    .loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      gap: 16px;
    }

    .loading-state p {
      color: var(--text-muted);
      font-size: 14px;
    }

    .spinner-large {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(99, 102, 241, 0.2);
      border-top-color: var(--primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    .spinner {
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
      display: inline-block;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-state {
      text-align: center;
      padding: 40px !important;
      color: var(--text-muted);
      font-style: italic;
    }

    .modal-sm {
      max-width: 420px;
    }

    .confirm-text {
      color: var(--text);
      margin-bottom: 8px;
      font-size: 15px;
      line-height: 1.5;
    }

    .confirm-warning {
      color: var(--danger);
      font-size: 13px;
      font-weight: 500;
    }

    .field-error {
      display: block;
      margin-top: 6px;
      color: var(--danger);
      font-size: 12px;
      font-weight: 500;
    }

    .toggle-group {
      margin-bottom: 18px;
    }

    .toggle-wrapper {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .toggle {
      position: relative;
      display: inline-block;
      width: 48px;
      height: 26px;
    }

    .toggle input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .toggle-slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid var(--border);
      border-radius: 26px;
      transition: all 0.3s;
    }

    .toggle-slider::before {
      content: '';
      position: absolute;
      height: 20px;
      width: 20px;
      left: 2px;
      bottom: 2px;
      background: white;
      border-radius: 50%;
      transition: all 0.3s;
    }

    .toggle input:checked + .toggle-slider {
      background: var(--success);
      border-color: var(--success);
    }

    .toggle input:checked + .toggle-slider::before {
      transform: translateX(22px);
    }

    .toggle-label {
      font-size: 14px;
      color: var(--text);
      font-weight: 500;
    }

    .checkbox-group {
      display: flex;
      gap: 20px;
      flex-wrap: wrap;
    }

    .checkbox-label {
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      font-size: 14px;
      color: var(--text);
      padding: 10px 16px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border);
      transition: all 0.3s;
    }

    .checkbox-label:hover {
      background: rgba(99, 102, 241, 0.08);
      border-color: var(--primary-light);
    }

    .checkbox-label input[type="checkbox"] {
      width: 18px;
      height: 18px;
      accent-color: var(--primary);
      cursor: pointer;
    }

    .page-header .btn .material-icons {
      font-size: 18px;
      margin-right: 6px;
      vertical-align: middle;
    }

    .page-header .btn {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  `]
})
export class UsersComponent implements OnInit {
  users = signal<User[]>([]);
  loading = signal(true);
  showForm = signal(false);
  showDeleteConfirm = signal(false);
  editingUser = signal<User | null>(null);
  deletingUser = signal<User | null>(null);
  submitting = signal(false);
  successMessage = signal('');
  errorMessage = signal('');

  selectedRoles: string[] = [];
  userForm: FormGroup;

  constructor(
    private userService: UserService,
    private fb: FormBuilder
  ) {
    this.userForm = this.fb.group({
      username: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: [''],
      confirmPassword: [''],
      fullName: ['', Validators.required],
      enabled: [true]
    }, { validators: UsersComponent.passwordsMatchValidator });
  }

  /**
   * Group-level validator: password and confirmPassword must match.
   * Only enforced when a password has been entered (so editing without
   * changing the password stays valid).
   */
  static passwordsMatchValidator(group: AbstractControl): ValidationErrors | null {
    const password = group.get('password')?.value ?? '';
    const confirm = group.get('confirmPassword')?.value ?? '';
    if (!password && !confirm) {
      return null;
    }
    return password === confirm ? null : { passwordMismatch: true };
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.userService.getAll().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Error al cargar usuarios');
        this.loading.set(false);
      }
    });
  }

  openCreateForm(): void {
    this.editingUser.set(null);
    this.selectedRoles = ['ROLE_USER'];
    this.userForm.reset({ username: '', email: '', password: '', confirmPassword: '', fullName: '', enabled: true });
    this.userForm.get('username')?.enable();
    this.userForm.get('password')?.setValidators(Validators.required);
    this.userForm.get('password')?.updateValueAndValidity();
    this.userForm.get('confirmPassword')?.setValidators(Validators.required);
    this.userForm.get('confirmPassword')?.updateValueAndValidity();
    this.showForm.set(true);
  }

  openEditForm(user: User): void {
    this.editingUser.set(user);
    this.selectedRoles = [...user.roles];
    this.userForm.patchValue({
      username: user.username,
      email: user.email,
      password: '',
      confirmPassword: '',
      fullName: user.fullName,
      enabled: user.enabled
    });
    this.userForm.get('username')?.disable();
    this.userForm.get('password')?.clearValidators();
    this.userForm.get('password')?.updateValueAndValidity();
    this.userForm.get('confirmPassword')?.clearValidators();
    this.userForm.get('confirmPassword')?.updateValueAndValidity();
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
    this.editingUser.set(null);
  }

  hasRole(role: string): boolean {
    return this.selectedRoles.includes(role);
  }

  toggleRole(role: string): void {
    const index = this.selectedRoles.indexOf(role);
    if (index > -1) {
      this.selectedRoles.splice(index, 1);
    } else {
      this.selectedRoles.push(role);
    }
  }

  onSubmit(): void {
    if (this.userForm.invalid) return;

    this.submitting.set(true);
    this.clearMessages();

    if (this.editingUser()) {
      const request: UpdateUserRequest = {
        email: this.userForm.get('email')?.value,
        fullName: this.userForm.get('fullName')?.value,
        enabled: this.userForm.get('enabled')?.value,
        roles: this.selectedRoles
      };
      const password = this.userForm.get('password')?.value;
      if (password) {
        request.password = password;
      }

      this.userService.update(this.editingUser()!.id, request).subscribe({
        next: () => {
          this.successMessage.set('Usuario actualizado correctamente');
          this.closeForm();
          this.loadUsers();
          this.submitting.set(false);
        },
        error: (err) => {
          this.errorMessage.set(err.error?.error || 'Error al actualizar usuario');
          this.submitting.set(false);
        }
      });
    } else {
      const request: CreateUserRequest = {
        username: this.userForm.get('username')?.value,
        email: this.userForm.get('email')?.value,
        password: this.userForm.get('password')?.value,
        fullName: this.userForm.get('fullName')?.value,
        roles: this.selectedRoles
      };

      this.userService.create(request).subscribe({
        next: () => {
          this.successMessage.set('Usuario creado correctamente');
          this.closeForm();
          this.loadUsers();
          this.submitting.set(false);
        },
        error: (err) => {
          this.errorMessage.set(err.error?.error || 'Error al crear usuario');
          this.submitting.set(false);
        }
      });
    }
  }

  confirmDelete(user: User): void {
    this.deletingUser.set(user);
    this.showDeleteConfirm.set(true);
  }

  cancelDelete(): void {
    this.showDeleteConfirm.set(false);
    this.deletingUser.set(null);
  }

  onDelete(): void {
    if (!this.deletingUser()) return;

    this.submitting.set(true);
    this.clearMessages();

    this.userService.delete(this.deletingUser()!.id).subscribe({
      next: () => {
        this.successMessage.set('Usuario eliminado correctamente');
        this.cancelDelete();
        this.loadUsers();
        this.submitting.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.error || 'Error al eliminar usuario');
        this.submitting.set(false);
      }
    });
  }

  private clearMessages(): void {
    this.successMessage.set('');
    this.errorMessage.set('');
  }
}
