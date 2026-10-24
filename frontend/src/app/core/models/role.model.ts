export type RoleName = 'ADMIN' | 'RECRUITER' | 'CANDIDATE';

export interface RoleOption {
  value: RoleName;
  label: string;
}

export const ROLE_LABELS: Record<RoleName, string> = {
  ADMIN: 'Administrateur',
  RECRUITER: 'Recruteur',
  CANDIDATE: 'Candidat',
};

export const ROLE_OPTIONS: readonly RoleOption[] = [
  { value: 'ADMIN', label: 'Administrateur' },
  { value: 'RECRUITER', label: 'Recruteur' },
  { value: 'CANDIDATE', label: 'Candidat' },
];
