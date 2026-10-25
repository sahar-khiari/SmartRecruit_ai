import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  CompetenceSuggeree,
  OffreFormPayload,
  STATUT_LABELS,
  StatutOffre,
} from '../../../core/models/offre-gestion.model';
import { OffreGestionService, messageErreur, splitListe } from '../../../core/services/offre-gestion.service';

type IaAction = 'description' | 'competences' | 'motsCles';

@Component({
  selector: 'app-create-offer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './create-offer.html',
  styleUrl: './create-offer.scss',
})
export class CreateOffer implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly offreGestionService = inject(OffreGestionService);

  protected readonly isEditMode = signal(false);
  protected readonly offerId = signal<number | null>(null);
  protected readonly loading = signal(false);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  // Formulaire
  protected titrePoste = '';
  protected description = '';
  protected niveauRequis = 'Intermédiaire';
  protected typeContrat = 'CDI';
  protected localisation = 'Tunis';
  protected dureeTestMinutes = 30;

  protected readonly niveaux = ['Junior', 'Intermédiaire', 'Senior', 'Expert'];
  protected readonly contrats = ['CDI', 'CDD', 'Freelance', 'Stage', 'Alternance'];

  // --- Module 2 : statut et assistant IA (Cohere) ---
  protected readonly statutLabels = STATUT_LABELS;
  protected readonly statut = signal<StatutOffre | null>(null);
  protected readonly iaEnCours = signal<IaAction | null>(null);
  protected readonly iaInfo = signal<string | null>(null);
  protected readonly suggestions = signal<CompetenceSuggeree[]>([]);
  protected readonly baseline = signal<string[]>([]);

  protected notesIa = '';
  protected competences: string[] = [];
  protected nouvelleCompetence = '';
  protected motsCles: string[] = [];

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = parseInt(idParam, 10);
      this.isEditMode.set(true);
      this.offerId.set(id);
      this.loadOfferForEdit(id);
    }
  }

  private loadOfferForEdit(id: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.offreGestionService.getById(id).subscribe({
      next: (offre) => {
        this.titrePoste = offre.titrePoste;
        this.description = offre.description;
        this.niveauRequis = offre.niveauRequis;
        this.typeContrat = offre.typeContrat;
        this.localisation = offre.localisation;
        this.competences = splitListe(offre.competencesRequises);
        this.dureeTestMinutes = offre.dureeTestMinutes;
        this.motsCles = splitListe(offre.motsCles);
        this.statut.set(offre.statut);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Impossible de charger les données de l\'offre.');
        this.loading.set(false);
      },
    });
  }

  /** publier = true : publication directe ; false : brouillon (création) ou simple mise à jour (modification). */
  protected save(publier = false): void {
    const erreur = this.valider();
    if (erreur) {
      this.errorMessage.set(erreur);
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const payload = this.payload();
    const id = this.offerId();
    const action$ = this.isEditMode() && id
      ? this.offreGestionService.update(id, payload)
      : this.offreGestionService.create({ ...payload, publier });

    action$.subscribe({
      next: (offre) => {
        // En modification, « Publier » publie un brouillon ou une offre clôturée.
        if (id && publier && (offre.statut === 'BROUILLON' || offre.statut === 'CLOTUREE')) {
          this.offreGestionService.publier(id).subscribe({
            next: () => this.retourListe(),
            error: (err) => this.echecSauvegarde(err),
          });
          return;
        }
        this.retourListe();
      },
      error: (err) => this.echecSauvegarde(err),
    });
  }

  protected cancel(): void {
    void this.router.navigate(['/app/recruteur/offres']);
  }

  // ------------------------------------------------------- Assistant IA (Cohere)

  protected genererDescription(): void {
    if (!this.titrePoste.trim()) {
      this.errorMessage.set('Indiquez au moins l\'intitulé du poste pour générer la description.');
      return;
    }
    this.lancerIa('description');
    this.offreGestionService
      .iaDescription({
        titrePoste: this.titrePoste,
        niveauRequis: this.niveauRequis,
        typeContrat: this.typeContrat,
        localisation: this.localisation,
        competences: this.competences.join(', '),
        notes: this.notesIa,
      })
      .subscribe({
        next: (res) => {
          this.description = res.description;
          this.terminerIa(
            `Description générée par ${res.modele}${res.depuisCache ? ' (cache)' : ''}. Relisez-la avant d'enregistrer.`,
          );
        },
        error: (err) => this.echecIa(err),
      });
  }

  protected suggererCompetences(): void {
    if (!this.titrePoste.trim()) {
      this.errorMessage.set('Indiquez au moins l\'intitulé du poste pour suggérer des compétences.');
      return;
    }
    this.lancerIa('competences');
    this.offreGestionService.iaCompetences(this.titrePoste, this.description, this.niveauRequis).subscribe({
      next: (res) => {
        this.suggestions.set(res.competences.filter((c) => !this.aCompetence(c.nom)));
        this.terminerIa(`${res.competences.length} compétences suggérées (méthode : ${res.methode}). Cliquez pour les ajouter.`);
      },
      error: (err) => this.echecIa(err),
    });
  }

  protected detecterMotsCles(): void {
    if (this.description.trim().length < 20) {
      this.errorMessage.set('Rédigez ou générez d\'abord la description (20 caractères minimum).');
      return;
    }
    this.lancerIa('motsCles');
    this.offreGestionService.iaMotsCles(this.titrePoste, this.description).subscribe({
      next: (res) => {
        this.motsCles = res.motsCles.map((m) => m.mot);
        this.baseline.set(res.motsClesBaseline);
        this.terminerIa(`${res.motsCles.length} mots-clés détectés par ${res.modele}.`);
      },
      error: (err) => this.echecIa(err),
    });
  }

  protected accepterSuggestion(s: CompetenceSuggeree): void {
    this.ajouterCompetence(s.nom);
    this.suggestions.update((list) => list.filter((x) => x.nom !== s.nom));
  }

  protected accepterToutes(): void {
    this.suggestions().forEach((s) => this.ajouterCompetence(s.nom));
    this.suggestions.set([]);
  }

  protected ajouterCompetenceSaisie(): void {
    splitListe(this.nouvelleCompetence).forEach((c) => this.ajouterCompetence(c));
    this.nouvelleCompetence = '';
  }

  protected retirerCompetence(nom: string): void {
    this.competences = this.competences.filter((c) => c !== nom);
  }

  protected retirerMotCle(mot: string): void {
    this.motsCles = this.motsCles.filter((m) => m !== mot);
  }

  // ----------------------------------------------------------------- privé

  private valider(): string | null {
    if (!this.titrePoste.trim() || !this.description.trim() || this.competences.length === 0) {
      return 'Veuillez remplir tous les champs obligatoires (intitulé, description, au moins une compétence).';
    }
    return null;
  }

  private payload(): OffreFormPayload {
    return {
      titrePoste: this.titrePoste.trim(),
      description: this.description.trim(),
      niveauRequis: this.niveauRequis,
      typeContrat: this.typeContrat,
      localisation: this.localisation.trim(),
      competencesRequises: this.competences.join(', '),
      dureeTestMinutes: this.dureeTestMinutes,
      motsCles: this.motsCles.length ? this.motsCles.join(', ') : null,
    };
  }

  private ajouterCompetence(nom: string): void {
    if (nom.trim() && !this.aCompetence(nom)) {
      this.competences = [...this.competences, nom.trim()];
    }
  }

  private aCompetence(nom: string): boolean {
    return this.competences.some((c) => c.toLowerCase() === nom.toLowerCase());
  }

  private lancerIa(action: IaAction): void {
    this.iaEnCours.set(action);
    this.iaInfo.set(null);
    this.errorMessage.set(null);
  }

  private terminerIa(info: string): void {
    this.iaEnCours.set(null);
    this.iaInfo.set(info);
  }

  private echecIa(err: unknown): void {
    this.iaEnCours.set(null);
    this.errorMessage.set(
      messageErreur(err, 'L\'IA est indisponible pour le moment.') +
        ' Vous pouvez continuer à remplir le formulaire manuellement.',
    );
  }

  private echecSauvegarde(err: unknown): void {
    this.submitting.set(false);
    this.errorMessage.set(messageErreur(err, 'Erreur lors de l\'enregistrement de l\'offre.'));
  }

  private retourListe(): void {
    this.submitting.set(false);
    void this.router.navigate(['/app/recruteur/offres']);
  }
}
