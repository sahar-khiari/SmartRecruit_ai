package com.smartrecruit.config;

import com.smartrecruit.entity.Role;
import com.smartrecruit.entity.RoleName;
import com.smartrecruit.entity.User;
import com.smartrecruit.repository.RoleRepository;
import com.smartrecruit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initialise les rôles (ADMIN, RECRUITER, CANDIDATE) et le compte administrateur
 * initial si aucun ADMIN n'existe. Aucun mot de passe n'est journalisé.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_DEV_PASSWORD = "Admin123!";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@smartrecruit.local}")
    private String adminEmail;

    @Value("${app.admin.password:Admin123!}")
    private String adminPassword;

    @Value("${app.admin.first-name:Admin}")
    private String adminFirstName;

    @Value("${app.admin.last-name:SmartRecruit}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(String... args) {
        initializeRoles();
        initializeAdmin();
    }

    private void initializeRoles() {
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(Role.builder().name(roleName).build());
                log.info("Rôle créé : {}", roleName);
            }
        }
    }

    private void initializeAdmin() {
        if (userRepository.existsByRole_Name(RoleName.ADMIN)) {
            log.info("Au moins un administrateur existe déjà : aucun compte ADMIN initial créé.");
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rôle ADMIN introuvable"));

        String email = adminEmail.toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            log.warn("Le compte {} existe déjà mais n'a pas le rôle ADMIN : création ignorée.", email);
            return;
        }

        User admin = User.builder()
                .firstName(adminFirstName.trim())
                .lastName(adminLastName.trim())
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .enabled(true)
                .role(adminRole)
                .build();
        userRepository.save(admin);

        log.info("Compte administrateur de développement créé : {} (mot de passe non affiché).", email);
        if (DEFAULT_DEV_PASSWORD.equals(adminPassword)) {
            log.warn("Le mot de passe ADMIN de développement par défaut est utilisé. "
                    + "Définissez ADMIN_PASSWORD avant tout déploiement.");
        }
    }
}
