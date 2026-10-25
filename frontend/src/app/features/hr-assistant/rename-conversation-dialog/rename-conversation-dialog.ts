import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { trimmedRequired } from '../message-composer/message-composer';

export const CONVERSATION_TITLE_MAX_LENGTH = 200;

export interface RenameConversationDialogData {
  title: string;
}

@Component({
  selector: 'app-rename-conversation-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './rename-conversation-dialog.html',
  styleUrl: './rename-conversation-dialog.scss',
})
export class RenameConversationDialog {
  private readonly dialogRef = inject(MatDialogRef<RenameConversationDialog, string>);
  protected readonly data = inject<RenameConversationDialogData>(MAT_DIALOG_DATA);

  protected readonly maxLength = CONVERSATION_TITLE_MAX_LENGTH;
  protected readonly control = new FormControl(this.data.title, {
    nonNullable: true,
    validators: [
      trimmedRequired,
      Validators.maxLength(CONVERSATION_TITLE_MAX_LENGTH),
    ],
  });

  protected submit(): void {
    this.control.markAsTouched();
    if (this.control.invalid) {
      return;
    }
    this.dialogRef.close(this.control.value.trim());
  }
}
