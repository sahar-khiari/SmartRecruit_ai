import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';
import { RoleLabelPipe } from '../../../shared/pipes/role-label.pipe';

interface CandidateShortcut {
  icon: string;
  title: string;
  description: string;
  path?: string;
}

@Component({
  selector: 'app-candidate-home',
  imports: [RouterLink, MatButtonModule, MatIconModule, RoleLabelPipe],
  templateUrl: './candidate-home.html',
  styleUrl: '../front-office-home.scss',
})
export class CandidateHome {
  protected readonly authService = inject(AuthService);

  protected readonly firstName = computed(
    () => this.authService.currentUser()?.firstName ?? '',
  );
  protected readonly initials = computed(() => {
    const user = this.authService.currentUser();
    return user
      ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase()
      : '?';
  });

  protected readonly shortcuts: readonly CandidateShortcut[] = [
    {
      icon: 'description',
      title: 'Mon CV',
      description: 'Déposez votre CV : l’IA remplit votre profil, vous validez.',
      path: '/app/candidate/cv',
    },
    {
      icon: 'badge',
      title: 'Mon profil candidat',
      description: 'Titre, expérience, compétences et liens visibles par les recruteurs.',
      path: '/app/candidate/profil',
    },
    {
      icon: 'work_outline',
      title: 'Consulter les offres',
      description: 'Parcourez les opportunités publiées par les recruteurs.',
      path: '/app/offres',
    },
    {
      icon: 'fact_check',
      title: 'Mes candidatures',
      description: 'Suivez l’avancement de vos candidatures.',
    },
    {
      icon: 'video_call',
      title: 'Mes entretiens',
      description: 'Retrouvez vos entretiens planifiés.',
    },
    {
      icon: 'smart_toy',
      title: 'Assistant RH',
      description: 'Préparez vos candidatures avec un accompagnement intelligent.',
      path: '/app/assistant-rh',
    },
  ];
}
