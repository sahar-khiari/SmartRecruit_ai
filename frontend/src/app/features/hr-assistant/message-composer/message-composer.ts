import { Component, effect, input, output } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export const MESSAGE_MAX_LENGTH = 10_000;

export function trimmedRequired(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value.length > 0 ? null : { required: true };
}

@Component({
  selector: 'app-message-composer',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './message-composer.html',
  styleUrl: './message-composer.scss',
})
export class MessageComposer {
  readonly disabled = input(false);
  readonly sending = input(false);
  readonly resetToken = input(0);

  readonly send = output<string>();

  protected readonly maxLength = MESSAGE_MAX_LENGTH;
  protected readonly control = new FormControl('', {
    nonNullable: true,
    validators: [trimmedRequired, Validators.maxLength(MESSAGE_MAX_LENGTH)],
  });

  private lastResetToken = 0;

  constructor() {
    effect(() => {
      const locked = this.disabled() || this.sending();
      if (locked && this.control.enabled) {
        this.control.disable({ emitEvent: false });
      } else if (!locked && this.control.disabled) {
        this.control.enable({ emitEvent: false });
      }
    });

    effect(() => {
      const token = this.resetToken();
      if (token !== this.lastResetToken) {
        this.lastResetToken = token;
        this.control.reset('');
      }
    });
  }

  protected get characterCount(): number {
    return this.control.value.length;
  }

  protected submit(): void {
    this.control.markAsTouched();

    if (this.disabled() || this.sending() || this.control.invalid) {
      return;
    }

    const content = this.control.value.trim();
    if (!content) {
      return;
    }

    this.send.emit(content);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.submit();
    }
  }
}
