export const environment = {
  production: false,
  appName: 'SmartRecruit AI',
  apiUrl: 'http://localhost:8080/api',
  aiServiceUrl: 'http://localhost:8001/api/v1',
  defaultPageSize: 10,
  pageSizeOptions: [5, 10, 20, 50],
} as const;
