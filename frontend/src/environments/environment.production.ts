export const environment = {
  production: true,
  appName: 'SmartRecruit AI',
  // En production, remplacer par l'URL de l'API déployée.
  apiUrl: 'http://localhost:8080/api',
  defaultPageSize: 10,
  pageSizeOptions: [5, 10, 20, 50],
} as const;
