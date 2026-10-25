import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Vérifie que la confirmation correspond au mot de passe.
 * Les noms de champs sont paramétrables (signup : password, change-password : newPassword).
 */
export function passwordsMatchValidator(
  passwordKey = 'password',
  confirmKey = 'confirmPassword',
): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordKey)?.value as string | null;
    const confirmation = group.get(confirmKey)?.value as string | null;

    if (!password && !confirmation) {
      return null;
    }

    return password === confirmation ? null : { passwordMismatch: true };
  };
}
