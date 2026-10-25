package com.smartrecruit.mapper;

import com.smartrecruit.dto.response.UserResponse;
import com.smartrecruit.entity.User;
import org.springframework.stereotype.Component;

/**
 * Convertit les entités User en DTO. Garantit que le mot de passe n'est jamais exposé.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().getName() : null,
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
