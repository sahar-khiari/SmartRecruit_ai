import { Pipe, PipeTransform } from '@angular/core';
import {
  KnowledgeCategory,
  knowledgeCategoryLabel,
} from '../../core/models/hr/knowledge-category.model';

@Pipe({
  name: 'knowledgeCategory',
  standalone: true,
})
export class KnowledgeCategoryPipe implements PipeTransform {
  transform(category: KnowledgeCategory | null | undefined): string {
    return category ? knowledgeCategoryLabel(category) : '';
  }
}
