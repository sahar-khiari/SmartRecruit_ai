import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Règles alignées sur le backend :
 * 8 à 100 caractères, au moins une majuscule, une minuscule, un chiffre
 * et un caractère spécial.
 */
export const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,100}$/;

export function passwordStrengthValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value as string | null;
  if (!value) {
    return null;
  }
  return PASSWORD_PATTERN.test(value) ? null : { passwordStrength: true };
}
