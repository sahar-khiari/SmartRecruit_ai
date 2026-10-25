package com.smartrecruit.service.impl;

import com.smartrecruit.dto.request.ChangePasswordRequest;
import com.smartrecruit.dto.request.UpdateProfileRequest;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.dto.response.UserResponse;
import com.smartrecruit.entity.Role;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.exception.EmailAlreadyExistsException;
import com.smartrecruit.exception.InvalidCredentialsException;
import com.smartrecruit.exception.ResourceNotFoundException;
import com.smartrecruit.mapper.UserMapper;
import com.smartrecruit.repository.RoleRepository;
import com.smartrecruit.repository.UserRepository;
import com.smartrecruit.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile() {
        return userMapper.toResponse(getCurrentUser());
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();
        String newEmail = normalizeEmail(request.email());

        if (!user.getEmail().equalsIgnoreCase(newEmail)
                && userRepository.existsByEmailAndIdNot(newEmail, user.getId())) {
            throw new EmailAlreadyExistsException(newEmail);
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(newEmail);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Le mot de passe actuel est incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable), userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userMapper.toResponse(findUserById(id));
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, boolean enabled) {
        User user = findUserById(id);
        user.setEnabled(enabled);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateRole(Long id, RoleName roleName) {
        User user = findUserById(id);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle", "name", roleName));
        user.setRole(role);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User currentAdmin = getCurrentUser();
        User target = findUserById(id);

        if (target.getId().equals(currentAdmin.getId())) {
            throw new IllegalArgumentException(
                    "Un administrateur ne peut pas supprimer son propre compte");
        }

        userRepository.delete(target);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new InvalidCredentialsException("Aucun utilisateur authentifié");
        }
        return userRepository.findByEmail(authentication.getName().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur connecté introuvable"));
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
