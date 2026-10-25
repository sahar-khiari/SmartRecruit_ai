/**
 * Format normalisé des erreurs renvoyées par le GlobalExceptionHandler du backend.
 */
export interface ApiError {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
  validationErrors?: Record<string, string>;
}
