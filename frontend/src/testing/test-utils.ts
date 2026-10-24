import { User } from '../app/core/models/user.model';

function base64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Construit un JWT factice (non signé) uniquement pour tester la lecture de `exp`.
 */
export function createJwt(expiresInSeconds: number): string {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64Url(
    JSON.stringify({
      sub: 'test@smartrecruit.local',
      role: 'CANDIDATE',
      iat: now,
      exp: now + expiresInSeconds,
    }),
  );
  return `${header}.${payload}.test-signature`;
}

export const TEST_USER: User = {
  id: 42,
  firstName: 'Amina',
  lastName: 'Benali',
  email: 'amina.benali@example.com',
  role: 'CANDIDATE',
  enabled: true,
  createdAt: '2026-10-01T09:30:00',
  updatedAt: '2026-10-02T11:15:00',
};

export const TEST_ADMIN: User = {
  ...TEST_USER,
  id: 1,
  firstName: 'Admin',
  lastName: 'SmartRecruit',
  email: 'admin@smartrecruit.local',
  role: 'ADMIN',
};
