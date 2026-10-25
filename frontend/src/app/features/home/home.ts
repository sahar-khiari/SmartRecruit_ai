import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

interface Feature {
  icon: string;
  title: string;
  description: string;
}

interface Space {
  icon: string;
  title: string;
  description: string;
  points: readonly string[];
  accent: 'primary' | 'secondary' | 'admin';
}

@Component({
  selector: 'app-home',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly features: readonly Feature[] = [
    {
      icon: 'description',
      title: 'Gestion des candidatures',
      description:
        'Centralisez les candidatures et suivez chaque étape du parcours, du dépôt du CV à la décision finale.',
    },
    {
      icon: 'post_add',
      title: 'Publication des offres',
      description:
        'Structurez vos offres, publiez-les rapidement et gardez une vue claire sur les postes ouverts.',
    },
    {
      icon: 'video_chat',
      title: 'Entretiens intelligents',
      description:
        'Planifiez les entretiens et préparez des échanges mieux informés grâce aux synthèses assistées.',
    },
    {
      icon: 'hub',
      title: 'Matching & recommandation',
      description:
        'Rapprochez les profils des besoins réels du poste avec un score de compatibilité explicable.',
    },
    {
      icon: 'smart_toy',
      title: 'Assistant RH intelligent',
      description:
        'Accompagnez les équipes RH dans la rédaction, l’analyse et la préparation des décisions.',
    },
  ];

  protected readonly spaces: readonly Space[] = [
    {
      icon: 'person_search',
      title: 'Espace candidat',
      description: 'Créez votre compte, gérez votre profil et suivez vos candidatures.',
      points: [
        'Inscription publique immédiate',
        'Profil personnel modifiable',
        'Mot de passe sécurisé',
      ],
      accent: 'primary',
    },
    {
      icon: 'business_center',
      title: 'Espace recruteur',
      description: 'Un futur espace dédié au suivi des offres et des talents.',
      points: [
        'Accès attribué par un administrateur',
        'Rôle RECRUITER',
        'Modules offres et candidats à venir',
      ],
      accent: 'secondary',
    },
    {
      icon: 'shield_person',
      title: 'Espace administrateur',
      description: 'Pilotez les comptes, les rôles et la disponibilité des utilisateurs.',
      points: [
        'Gestion complète des utilisateurs',
        'Activation et désactivation',
        'Attribution des rôles',
      ],
      accent: 'admin',
    },
  ];
}
