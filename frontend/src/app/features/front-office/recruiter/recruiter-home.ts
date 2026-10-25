import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';
import { RoleLabelPipe } from '../../../shared/pipes/role-label.pipe';

interface RecruiterShortcut {
  icon: string;
  title: string;
  description: string;
  path?: string;
}

@Component({
  selector: 'app-recruiter-home',
  imports: [RouterLink, MatButtonModule, MatIconModule, RoleLabelPipe],
  templateUrl: './recruiter-home.html',
  styleUrl: '../front-office-home.scss',
})
export class RecruiterHome {
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

  protected readonly shortcuts: readonly RecruiterShortcut[] = [
    {
      icon: 'post_add',
      title: 'Créer une offre',
      description: 'Publiez une nouvelle opportunité de recrutement.',
      path: '/app/recruteur/offres/new',
    },
    {
      icon: 'work_outline',
      title: 'Mes offres',
      description: 'Pilotez les offres publiées par votre organisation.',
      path: '/app/recruteur/offres',
    },
    {
      icon: 'inbox',
      title: 'Candidatures reçues',
      description: 'Consultez les profils ayant postulé à vos offres.',
      path: '/app/recruteur/offres',
    },
    {
      icon: 'video_call',
      title: 'Entretiens',
      description: 'Organisez et suivez vos échanges avec les candidats.',
    },
    {
      icon: 'smart_toy',
      title: 'Assistant RH',
      description: 'Préparez vos décisions avec un accompagnement intelligent.',
      path: '/app/assistant-rh',
    },
  ];
}
