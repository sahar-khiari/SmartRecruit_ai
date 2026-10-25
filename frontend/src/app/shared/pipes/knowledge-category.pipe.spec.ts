import { KnowledgeCategoryPipe } from './knowledge-category.pipe';

describe('KnowledgeCategoryPipe', () => {
  const pipe = new KnowledgeCategoryPipe();

  it('traduit toutes les catégories backend en français', () => {
    expect(pipe.transform('FAQ')).toBe('FAQ');
    expect(pipe.transform('CANDIDATE_GUIDE')).toBe('Guide candidat');
    expect(pipe.transform('RECRUITER_GUIDE')).toBe('Guide recruteur');
    expect(pipe.transform('INTERVIEW_GUIDE')).toBe("Guide d'entretien");
    expect(pipe.transform('RECRUITMENT_PROCEDURE')).toBe('Procédure de recrutement');
    expect(pipe.transform('PLATFORM_HELP')).toBe('Aide de la plateforme');
    expect(pipe.transform('HR_POLICY')).toBe('Politique RH');
    expect(pipe.transform('OTHER')).toBe('Autre');
  });

  it('renvoie une chaîne vide pour une valeur absente', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
