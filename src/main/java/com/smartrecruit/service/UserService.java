package com.smartrecruit.service;

import com.smartrecruit.dto.request.ChangePasswordRequest;
import com.smartrecruit.dto.request.UpdateProfileRequest;
import com.smartrecruit.dto.response.PageResponse;
import com.smartrecruit.dto.response.UserResponse;
import com.smartrecruit.entity.RoleName;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse getCurrentUserProfile();

    UserResponse updateProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);

    PageResponse<UserResponse> getAllUsers(Pageable pageable);

    UserResponse getUserById(Long id);

    UserResponse updateStatus(Long id, boolean enabled);

    UserResponse updateRole(Long id, RoleName role);

    void deleteUser(Long id);
}
