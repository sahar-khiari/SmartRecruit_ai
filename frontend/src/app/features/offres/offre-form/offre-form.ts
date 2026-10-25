import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  CompetenceSuggeree,
  MODE_TRAVAIL_LABELS,
  ModeTravail,
  OffreFormPayload,
  STATUT_LABELS,
  StatutOffre,
} from '../../../core/models/offre-gestion.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { OffreGestionService, messageErreur, splitListe } from '../../../core/services/offre-gestion.service';

type IaAction = 'description' | 'competences' | 'motsCles';

/**
 * Module 2 : création / modification d'une offre par le recruteur, avec 3 assistants IA (Cohere).
 * Les propositions de l'IA sont modifiables : le recruteur garde toujours la décision finale.
 */
@Component({
  selector: 'app-offre-form',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './offre-form.html',
  styleUrls: ['../offres-shared.scss', './offre-form.scss'],
})
export class OffreForm implements OnInit, HasUnsavedChanges {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(OffreGestionService);

  protected readonly niveaux = ['Junior', 'Intermédiaire', 'Senior', 'Expert'];
  protected readonly contrats = ['CDI', 'CDD', 'Stage', 'Alternance', 'Freelance'];
  protected readonly modes = Object.entries(MODE_TRAVAIL_LABELS) as [ModeTravail, string][];
  protected readonly statutLabels = STATUT_LABELS;

  protected readonly offreId = signal<number | null>(null);
  protected readonly statut = signal<StatutOffre | null>(null);
  protected readonly motifSuspension = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly iaEnCours = signal<IaAction | null>(null);
  protected readonly iaInfo = signal<string | null>(null);
  protected readonly suggestions = signal<CompetenceSuggeree[]>([]);
  protected readonly baseline = signal<string[]>([]);

  // Champs du formulaire
  protected titrePoste = '';
  protected entreprise = '';
  protected localisation = '';
  protected typeContrat = 'CDI';
  protected modeTravail: ModeTravail | '' = 'HYBRIDE';
  protected niveauRequis = 'Junior';
  protected experienceMinAnnees: number | null = null;
  protected salaireMin: number | null = null;
  protected salaireMax: number | null = null;
  protected dateLimite = '';
  protected dureeTestMinutes = 30;
  protected notesIa = '';
  protected description = '';
  protected competences: string[] = [];
  protected nouvelleCompetence = '';
  protected motsCles: string[] = [];

  /** Photo du formulaire au chargement, pour détecter les modifications non enregistrées. */
  private etatInitial = '';
  private enregistre = false;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.offreId.set(id);
      this.charger(id);
    } else {
      this.etatInitial = JSON.stringify(this.payload());
    }
  }

  hasUnsavedChanges(): boolean {
    return !this.enregistre && !this.loading() && JSON.stringify(this.payload()) !== this.etatInitial;
  }

  protected get isEdit(): boolean {
    return this.offreId() !== null;
  }

  // -------------------------------------------------------------- IA Cohere

  protected genererDescription(): void {
    if (!this.titrePoste.trim()) {
      this.error.set("Indiquez au moins l'intitulé du poste pour générer la description.");
      return;
    }
    this.lancerIa('description');
    this.service
      .iaDescription({
        titrePoste: this.titrePoste,
        niveauRequis: this.niveauRequis,
        typeContrat: this.typeContrat,
        localisation: this.localisation,
        modeTravail: this.modeTravail ? MODE_TRAVAIL_LABELS[this.modeTravail] : undefined,
        entreprise: this.entreprise,
        competences: this.competences.join(', '),
        notes: this.notesIa,
      })
      .subscribe({
        next: (res) => {
          this.description = res.description;
          this.terminerIa(`Description générée par ${res.modele}${res.depuisCache ? ' (cache)' : ''}. Relisez-la avant d'enregistrer.`);
        },
        error: (err) => this.echecIa(err),
      });
  }

  protected suggererCompetences(): void {
    if (!this.titrePoste.trim()) {
      this.error.set("Indiquez au moins l'intitulé du poste pour suggérer des compétences.");
      return;
    }
    this.lancerIa('competences');
    this.service.iaCompetences(this.titrePoste, this.description, this.niveauRequis).subscribe({
      next: (res) => {
        this.suggestions.set(res.competences.filter((c) => !this.aCompetence(c.nom)));
        this.terminerIa(
          `${res.competences.length} compétences suggérées (méthode : ${res.methode}). Cliquez pour les ajouter.`,
        );
      },
      error: (err) => this.echecIa(err),
    });
  }

  protected detecterMotsCles(): void {
    if (this.description.trim().length < 20) {
      this.error.set("Rédigez ou générez d'abord la description (20 caractères minimum).");
      return;
    }
    this.lancerIa('motsCles');
    this.service.iaMotsCles(this.titrePoste, this.description).subscribe({
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

  // ------------------------------------------------------ chips manuelles

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

  // ---------------------------------------------------------- enregistrement

  protected enregistrer(publier: boolean): void {
    const erreur = this.valider();
    if (erreur) {
      this.error.set(erreur);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const payload = this.payload();
    const id = this.offreId();

    const action$ = id
      ? this.service.update(id, payload)
      : this.service.create({ ...payload, publier });

    action$.subscribe({
      next: (offre) => {
        // En modification, « Enregistrer et publier » publie un brouillon ou une offre clôturée.
        if (id && publier && (offre.statut === 'BROUILLON' || offre.statut === 'CLOTUREE')) {
          this.service.publier(id).subscribe({
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

  // ----------------------------------------------------------------- privé

  private charger(id: number): void {
    this.loading.set(true);
    this.service.getById(id).subscribe({
      next: (o) => {
        this.titrePoste = o.titrePoste;
        this.entreprise = o.entreprise ?? '';
        this.localisation = o.localisation ?? '';
        this.typeContrat = o.typeContrat || 'CDI';
        this.modeTravail = o.modeTravail ?? '';
        this.niveauRequis = o.niveauRequis;
        this.experienceMinAnnees = o.experienceMinAnnees;
        this.salaireMin = o.salaireMin;
        this.salaireMax = o.salaireMax;
        this.dateLimite = o.dateLimite ?? '';
        this.dureeTestMinutes = o.dureeTestMinutes || 30;
        this.description = o.description;
        this.competences = splitListe(o.competencesRequises);
        this.motsCles = splitListe(o.motsCles);
        this.statut.set(o.statut);
        this.motifSuspension.set(o.motifSuspension);
        this.etatInitial = JSON.stringify(this.payload());
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(messageErreur(err, "Impossible de charger l'offre."));
        this.loading.set(false);
      },
    });
  }

  private valider(): string | null {
    if (!this.titrePoste.trim()) return "L'intitulé du poste est obligatoire.";
    if (!this.description.trim()) return 'La description est obligatoire (vous pouvez la générer avec l’IA).';
    if (this.competences.length === 0) return 'Ajoutez au moins une compétence requise.';
    if (this.salaireMin !== null && this.salaireMax !== null && this.salaireMin > this.salaireMax) {
      return 'Le salaire minimum doit être inférieur ou égal au salaire maximum.';
    }
    return null;
  }

  private payload(): OffreFormPayload {
    return {
      titrePoste: this.titrePoste.trim(),
      description: this.description.trim(),
      niveauRequis: this.niveauRequis,
      competencesRequises: this.competences.join(', '),
      localisation: this.localisation.trim(),
      typeContrat: this.typeContrat,
      dureeTestMinutes: this.dureeTestMinutes,
      entreprise: this.entreprise.trim() || null,
      modeTravail: this.modeTravail || null,
      experienceMinAnnees: this.experienceMinAnnees,
      salaireMin: this.salaireMin,
      salaireMax: this.salaireMax,
      dateLimite: this.dateLimite || null,
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
    this.error.set(null);
  }

  private terminerIa(info: string): void {
    this.iaEnCours.set(null);
    this.iaInfo.set(info);
  }

  private echecIa(err: unknown): void {
    this.iaEnCours.set(null);
    this.error.set(
      messageErreur(err, "L'IA est indisponible pour le moment.") +
        '\nVous pouvez continuer à remplir le formulaire manuellement.',
    );
  }

  private echecSauvegarde(err: unknown): void {
    this.saving.set(false);
    this.error.set(messageErreur(err, "Erreur lors de l'enregistrement de l'offre."));
  }

  private retourListe(): void {
    this.saving.set(false);
    this.enregistre = true;
    void this.router.navigate(['/app/recruteur/offres']);
  }
}
