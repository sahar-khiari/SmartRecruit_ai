package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.LoginRequest;
import com.smartrecruit.dto.request.SignupRequest;
import com.smartrecruit.dto.response.AuthResponse;
import com.smartrecruit.dto.response.MessageResponse;
import com.smartrecruit.entity.Role;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.AccountDisabledException;
import com.smartrecruit.exception.EmailAlreadyExistsException;
import com.smartrecruit.exception.InvalidCredentialsException;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.mapper.UserMapper;
import com.smartrecruit.repository.RoleRepository;
import com.smartrecruit.repository.UserRepository;
import com.smartrecruit.security.JwtService;
import com.smartrecruit.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public MessageResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Role candidateRole = roleRepository.findByName(RoleName.CANDIDATE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rôle", "name", RoleName.CANDIDATE));

        // Une inscription publique reçoit TOUJOURS le rôle CANDIDATE.
        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .role(candidateRole)
                .build();

        userRepository.save(user);

        return new MessageResponse("Utilisateur créé avec succès");
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new InvalidCredentialsException();
            }
        } catch (DisabledException ex) {
            throw new AccountDisabledException();
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationMs(),
                userMapper.toResponse(user)
        );
    }

    @Override
    public MessageResponse logout() {
        return new MessageResponse(
                "Déconnexion : supprimez le token JWT côté client. "
                        + "Aucune révocation serveur n'est implémentée (pas de blacklist).");
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
