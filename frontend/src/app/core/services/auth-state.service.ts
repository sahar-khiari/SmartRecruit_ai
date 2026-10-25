import { computed, Injectable, signal } from '@angular/core';
import { User } from '../models/user.model';

/**
 * État de session réactif, sans dépendance à HttpClient.
 * Permet aux intercepteurs, guards et composants de partager le même état.
 */
@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly userSignal = signal<User | null>(null);

  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.userSignal() !== null);

  setUser(user: User): void {
    this.userSignal.set(user);
  }

  clear(): void {
    this.userSignal.set(null);
  }
}
