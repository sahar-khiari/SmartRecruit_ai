package com.smartrecruit.matching.engine;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Calcul de la similarité cosinus TF-IDF entre deux textes.
 *
 * Implémentation 100 % Java, sans bibliothèque externe.
 *
 * TF(t, d)  = fréquence du terme t dans le document d
 * IDF(t)    = log(1 + N / (1 + df(t)))  [lissage +1]
 * TF-IDF(t) = TF(t, d) × IDF(t)
 *
 * La similarité cosinus est le produit scalaire des vecteurs TF-IDF normalisés.
 * Résultat dans [0, 1] : 0 = aucun terme commun, 1 = textes identiques.
 */
@Component
public class TfIdfCalculator {

    /** Expressions séparables : tout caractère non alphanumérique non accentué. */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-ZÀ-ÿ0-9+#]+");

    /** Mots vides (FR + EN) exclus du calcul pour réduire le bruit. */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            // Français
            "le","la","les","un","une","des","et","ou","de","du","au","aux","en",
            "dans","par","pour","sur","avec","que","qui","quoi","dont","où","se",
            "son","sa","ses","mon","ma","mes","il","elle","ils","elles","je","tu",
            "nous","vous","ce","cet","cette","ces","est","sont","été","avoir","être",
            "faire","an","ans","mais","donc","or","ni","car","à",
            // Anglais
            "the","a","an","and","or","in","of","to","for","with","on","at","by",
            "from","is","are","was","were","be","been","have","has","had","do","does",
            "did","will","would","could","should","may","might","this","that","these",
            "those","we","you","they","he","she","it","not","no","as","if","but","so"
    ));

    /**
     * Calcule la similarité cosinus TF-IDF entre deux textes.
     *
     * @param text1 premier texte (ex. : CV)
     * @param text2 deuxième texte (ex. : description de poste)
     * @return score de similarité dans [0, 1]
     */
    public double similarity(String text1, String text2) {
        if (text1 == null || text2 == null
                || text1.isBlank() || text2.isBlank()) {
            return 0.0;
        }

        Map<String, Integer> freq1 = termFrequency(text1);
        Map<String, Integer> freq2 = termFrequency(text2);

        // Corpus de 2 documents pour le calcul IDF
        List<Map<String, Integer>> corpus = List.of(freq1, freq2);
        Set<String> vocabulary = new HashSet<>(freq1.keySet());
        vocabulary.addAll(freq2.keySet());

        // Vecteurs TF-IDF
        Map<String, Double> tfidf1 = tfidfVector(freq1, vocabulary, corpus);
        Map<String, Double> tfidf2 = tfidfVector(freq2, vocabulary, corpus);

        return cosineSimilarity(tfidf1, tfidf2);
    }

    /**
     * Tokenise un texte et calcule les fréquences de termes.
     * Les mots vides et les tokens de moins de 2 caractères sont ignorés.
     */
    Map<String, Integer> termFrequency(String text) {
        String[] tokens = TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT));
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            String t = token.trim();
            if (t.length() >= 2 && !STOP_WORDS.contains(t)) {
                freq.merge(t, 1, Integer::sum);
            }
        }
        return freq;
    }

    /** Calcule le vecteur TF-IDF d'un document dans un corpus. */
    private Map<String, Double> tfidfVector(
            Map<String, Integer> freq,
            Set<String> vocabulary,
            List<Map<String, Integer>> corpus) {

        int totalTerms = freq.values().stream().mapToInt(Integer::intValue).sum();
        if (totalTerms == 0) return Collections.emptyMap();

        Map<String, Double> vector = new HashMap<>();
        int N = corpus.size();

        for (String term : vocabulary) {
            if (!freq.containsKey(term)) continue;
            double tf = (double) freq.get(term) / totalTerms;
            long df = corpus.stream().filter(d -> d.containsKey(term)).count();
            double idf = Math.log(1.0 + (double) N / (1.0 + df));
            vector.put(term, tf * idf);
        }
        return vector;
    }

    /** Cosinus de l'angle entre deux vecteurs (produit scalaire / norme₁×norme₂). */
    private double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        if (v1.isEmpty() || v2.isEmpty()) return 0.0;

        double dot = 0.0;
        for (Map.Entry<String, Double> e : v1.entrySet()) {
            double val2 = v2.getOrDefault(e.getKey(), 0.0);
            dot += e.getValue() * val2;
        }

        double norm1 = Math.sqrt(v1.values().stream().mapToDouble(x -> x * x).sum());
        double norm2 = Math.sqrt(v2.values().stream().mapToDouble(x -> x * x).sum());

        if (norm1 == 0 || norm2 == 0) return 0.0;
        return Math.min(1.0, dot / (norm1 * norm2)); // clamp à 1.0 par sécurité
    }
}
