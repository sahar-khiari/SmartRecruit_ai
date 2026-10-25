import { Pipe, PipeTransform } from '@angular/core';
import { ROLE_LABELS, RoleName } from '../../core/models/role.model';

@Pipe({
  name: 'roleLabel',
})
export class RoleLabelPipe implements PipeTransform {
  transform(value: RoleName | string | null | undefined): string {
    if (!value) {
      return '';
    }
    return ROLE_LABELS[value as RoleName] ?? value;
  }
}
