import { TestBed } from '@angular/core/testing';
import { TokenStorageService } from './token-storage.service';
import { createJwt, TEST_USER } from '../../../testing/test-utils';

describe('TokenStorageService', () => {
  let service: TokenStorageService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenStorageService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('enregistre, lit et supprime le token', () => {
    const token = createJwt(3600);

    service.saveToken(token);

    expect(service.getToken()).toBe(token);
    expect(service.hasToken()).toBe(true);

    service.clearSession();

    expect(service.getToken()).toBeNull();
    expect(service.hasToken()).toBe(false);
  });

  it('enregistre uniquement les données utilisateur non sensibles', () => {
    service.saveUser(TEST_USER);

    const user = service.getUser();

    expect(user).toEqual(TEST_USER);
    expect(JSON.stringify(user)).not.toContain('password');
  });

  it('ignore un utilisateur stocké au format invalide', () => {
    localStorage.setItem('smartrecruit.current_user', '{invalide');

    expect(service.getUser()).toBeNull();
  });

  it('détecte un token valide', () => {
    const token = createJwt(3600);

    expect(service.isTokenExpired(token)).toBe(false);
    expect(service.getTokenExpirationDate(token)).toBeInstanceOf(Date);
  });

  it('détecte un token expiré', () => {
    const token = createJwt(-60);

    expect(service.isTokenExpired(token)).toBe(true);
  });

  it('considère un token absent ou malformé comme expiré', () => {
    expect(service.isTokenExpired(null)).toBe(true);
    expect(service.isTokenExpired('token-invalide')).toBe(true);
    expect(service.isTokenExpired('a.b.c')).toBe(true);
  });

  it('nettoie le token et l’utilisateur ensemble', () => {
    service.saveToken(createJwt(3600));
    service.saveUser(TEST_USER);

    service.clearSession();

    expect(localStorage.getItem('smartrecruit.access_token')).toBeNull();
    expect(localStorage.getItem('smartrecruit.current_user')).toBeNull();
  });
});
