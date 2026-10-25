package com.smartrecruit.service;

import com.smartrecruit.dto.response.CandidateCvResponse;
import com.smartrecruit.entity.CandidateCv;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Dépôt et consultation du CV PDF des candidats (Module 1 - étape 2).
 */
public interface CandidateCvService {

    CandidateCvResponse uploadMyCv(MultipartFile file);

    CandidateCvResponse getMyCv();

    /** Fichier du CV courant d'un candidat : RECRUITER, ADMIN ou le candidat lui-même. */
    CvFile getCvFile(Long candidateId);

    /** Contenu d'un CV stocké, pour l'envoyer au service IA. */
    byte[] readContent(CandidateCv cv);

    /** Clés de stockage de tous les CV d'un candidat, à relever avant la suppression de son compte. */
    List<String> findStorageKeys(Long candidateId);

    /** Supprime les fichiers sur disque (données personnelles) une fois le compte supprimé en base. */
    void deleteFiles(List<String> storageKeys);

    record CvFile(String originalFilename, long sizeBytes, byte[] content) {
    }
}
