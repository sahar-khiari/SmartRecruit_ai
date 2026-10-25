import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { map } from 'rxjs';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '../../shared/components/confirmation-dialog/confirmation-dialog';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/**
 * Confirme la sortie d'un formulaire contenant des modifications non enregistrées.
 * Le dialogue réutilise le composant partagé de confirmation.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (!component?.hasUnsavedChanges?.()) {
    return true;
  }

  const dialog = inject(MatDialog);
  return dialog
    .open<ConfirmationDialog, ConfirmationDialogData, boolean>(ConfirmationDialog, {
      data: {
        title: 'Modifications non enregistrées',
        message:
          'Vous avez commencé à modifier ce document. Quitter sans enregistrer perdra ces changements.',
        confirmLabel: 'Quitter sans enregistrer',
        cancelLabel: 'Continuer la saisie',
        danger: true,
      },
      width: '480px',
      autoFocus: 'dialog',
    })
    .afterClosed()
    .pipe(map((confirmed) => confirmed === true));
};
