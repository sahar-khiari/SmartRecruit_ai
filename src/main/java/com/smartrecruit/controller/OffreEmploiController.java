package com.smartrecruit.controller;

import com.smartrecruit.dto.request.CandidatureRequest;
import com.smartrecruit.dto.request.OffreEmploiCreateRequest;
import com.smartrecruit.dto.response.CandidatureResponse;
import com.smartrecruit.dto.response.OffreEmploiResponse;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.entity.CandidatureStatut;
import com.smartrecruit.entity.User;
import com.smartrecruit.service.CandidatureService;
import com.smartrecruit.service.CurrentUserService;
import com.smartrecruit.service.OffreEmploiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Contrôleur REST pour les offres d'emploi et les candidatures associées.
 */
@RestController
@RequestMapping("/api/offres")
@RequiredArgsConstructor
public class OffreEmploiController {

    private final OffreEmploiService offreEmploiService;
    private final CandidatureService candidatureService;
    private final CurrentUserService currentUserService;

    // -------------------------------------------------------------------------
    // Offres d'emploi
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<PageResponse<OffreEmploiResponse>> listOffresActives(
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<OffreEmploiResponse> page = offreEmploiService.listOffresActives(pageable);
        return ResponseEntity.ok(PageResponse.from(page, Function.identity()));
    }

    @GetMapping("/mes-offres")
    public ResponseEntity<PageResponse<OffreEmploiResponse>> listMesOffres(
            @PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        Page<OffreEmploiResponse> page = offreEmploiService.listOffresRecruteur(currentUser.getId(), pageable);
        return ResponseEntity.ok(PageResponse.from(page, Function.identity()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OffreEmploiResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(offreEmploiService.getById(id));
    }

    @PostMapping
    public ResponseEntity<OffreEmploiResponse> create(@Valid @RequestBody OffreEmploiCreateRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(offreEmploiService.create(request, currentUser));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OffreEmploiResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody OffreEmploiCreateRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(offreEmploiService.update(id, request, currentUser));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OffreEmploiResponse> toggleStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        User currentUser = currentUserService.getCurrentUser();
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return ResponseEntity.ok(offreEmploiService.toggleStatus(id, active, currentUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User currentUser = currentUserService.getCurrentUser();
        offreEmploiService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Candidatures
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/candidater")
    public ResponseEntity<CandidatureResponse> candidater(
            @PathVariable Long id,
            @RequestBody(required = false) CandidatureRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidatureService.candidater(id, request, currentUser));
    }

    @GetMapping("/{id}/candidatures")
    public ResponseEntity<List<CandidatureResponse>> getCandidatures(@PathVariable Long id) {
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(candidatureService.getCandidaturesParOffre(id, currentUser));
    }

    @GetMapping("/mes-candidatures")
    public ResponseEntity<List<CandidatureResponse>> getMesCandidatures() {
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(candidatureService.getMesCandidatures(currentUser));
    }

    @GetMapping("/{id}/ma-candidature")
    public ResponseEntity<CandidatureResponse> getMaCandidature(@PathVariable Long id) {
        User currentUser = currentUserService.getCurrentUser();
        CandidatureResponse res = candidatureService.getMaCandidatureParOffre(id, currentUser);
        if (res == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(res);
    }
}
