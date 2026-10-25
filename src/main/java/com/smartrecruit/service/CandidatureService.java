package com.smartrecruit.service;

import com.smartrecruit.dto.request.CandidatureRequest;
import com.smartrecruit.dto.response.CandidatureResponse;
import com.smartrecruit.entity.CandidatureStatut;
import com.smartrecruit.entity.User;

import java.util.List;

/**
 * Service métier pour la gestion des candidatures.
 */
public interface CandidatureService {

    CandidatureResponse candidater(Long offreId, CandidatureRequest request, User candidat);

    List<CandidatureResponse> getCandidaturesParOffre(Long offreId, User currentUser);

    List<CandidatureResponse> getMesCandidatures(User candidat);

    CandidatureResponse updateStatut(Long candidatureId, CandidatureStatut statut, User currentUser);
}
