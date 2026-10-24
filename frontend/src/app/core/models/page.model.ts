/**
 * Réponse paginée réelle du backend : PageResponse<T> (Spring Data Page mappée).
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
