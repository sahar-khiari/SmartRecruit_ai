import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CandidateService } from '../../../core/services/candidate.service';
import { CandidateProfile, CandidateSkill, EXPERIENCE_LEVEL_LABELS } from '../../../core/models/candidate.model';
import { getApiErrorMessage } from '../../../core/utils/http-error.util';

const URL_PATTERN = /^https?:\/\/\S+$/;

/**
 * Module 1 - profil métier du candidat (titre, coordonnées, liens, expérience, compétences).
 * Le niveau d'expérience est calculé par le serveur à partir des années.
 */
@Component({
  selector: 'app-my-profile',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './my-profile.html',
  styleUrl: './my-profile.scss',
})
export class MyProfile implements OnInit {
  private readonly candidateService = inject(CandidateService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  protected readonly levelLabels = EXPERIENCE_LEVEL_LABELS;
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly profile = signal<CandidateProfile | null>(null);
  protected readonly skills = signal<CandidateSkill[]>([]);
  protected newSkill = '';

  protected readonly form = this.fb.group({
    headline: ['', Validators.maxLength(150)],
    phone: ['', Validators.pattern(/^[+0-9 ().-]{6,30}$/)],
    city: ['', Validators.maxLength(100)],
    githubUrl: ['', Validators.pattern(URL_PATTERN)],
    linkedinUrl: ['', Validators.pattern(URL_PATTERN)],
    summary: ['', Validators.maxLength(2000)],
    yearsOfExperience: [null as number | null, [Validators.min(0), Validators.max(60)]],
  });

  ngOnInit(): void {
    this.candidateService.getMyProfile().subscribe({
      next: (profile) => this.setProfile(profile),
      error: (error) => {
        this.loadError.set(getApiErrorMessage(error, 'Impossible de charger votre profil.'));
        this.loading.set(false);
      },
    });
  }

  protected addSkill(): void {
    const name = this.newSkill.trim().replace(/\s+/g, ' ');
    if (name && !this.skills().some((s) => s.name.toLowerCase() === name.toLowerCase())) {
      this.skills.update((skills) => [...skills, { id: 0, name, source: 'MANUAL' }]);
    }
    this.newSkill = '';
  }

  protected removeSkill(skill: CandidateSkill): void {
    this.skills.update((skills) => skills.filter((s) => s !== skill));
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const clean = (text: string | null) => (text?.trim() ? text.trim() : null);
    this.saving.set(true);

    // Séquentiel : le premier appel peut créer le profil, le second ne doit pas le créer en parallèle.
    this.candidateService.updateMyProfile({
        headline: clean(value.headline),
        phone: clean(value.phone),
        city: clean(value.city),
        githubUrl: clean(value.githubUrl),
        linkedinUrl: clean(value.linkedinUrl),
        summary: clean(value.summary),
        experienceLevel: null,
        yearsOfExperience: value.yearsOfExperience ?? null,
      })
      .pipe(switchMap(() => this.candidateService.updateMySkills(this.skills().map((s) => s.name))))
      .subscribe({
      next: (profile) => {
        this.setProfile(profile);
        this.saving.set(false);
        this.snackBar.open('Profil enregistré.', 'OK', { duration: 3000 });
      },
      error: (error) => {
        this.saving.set(false);
        this.snackBar.open(getApiErrorMessage(error, 'Enregistrement impossible.'), 'OK', { duration: 6000 });
      },
    });
  }

  private setProfile(profile: CandidateProfile): void {
    this.profile.set(profile);
    this.skills.set(profile.skills);
    this.form.reset({
      headline: profile.headline ?? '',
      phone: profile.phone ?? '',
      city: profile.city ?? '',
      githubUrl: profile.githubUrl ?? '',
      linkedinUrl: profile.linkedinUrl ?? '',
      summary: profile.summary ?? '',
      yearsOfExperience: profile.yearsOfExperience,
    });
    this.loading.set(false);
  }
}
