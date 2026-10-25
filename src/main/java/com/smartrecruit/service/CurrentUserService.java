package com.smartrecruit.service;

import com.smartrecruit.entity.User;
import com.smartrecruit.exception.InvalidCredentialsException;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Point unique de résolution de l'utilisateur authentifié.
 * L'identité provient exclusivement du contexte Spring Security alimenté par le JWT.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !StringUtils.hasText(authentication.getName())) {
            throw new InvalidCredentialsException("Aucun utilisateur authentifié");
        }

        String email = authentication.getName().trim().toLowerCase();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur connecté introuvable"));
    }
}
