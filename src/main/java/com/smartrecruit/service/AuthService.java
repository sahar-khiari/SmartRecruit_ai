package com.smartrecruit.service;

import com.smartrecruit.dto.request.LoginRequest;
import com.smartrecruit.dto.request.SignupRequest;
import com.smartrecruit.dto.response.AuthResponse;
import com.smartrecruit.dto.response.MessageResponse;

public interface AuthService {

    MessageResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);

    MessageResponse logout();
}
