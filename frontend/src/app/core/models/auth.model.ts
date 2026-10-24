import { User } from './user.model';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface MessageResponse {
  message: string;
}

export interface JwtPayload {
  sub?: string;
  role?: string;
  iat?: number;
  exp?: number;
}
