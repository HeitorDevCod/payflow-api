package com.payflow.auth.application;

import com.payflow.auth.api.AuthDtos.LoginRequest;
import com.payflow.auth.api.AuthDtos.RegisterRequest;
import com.payflow.auth.api.AuthDtos.TokenResponse;
import com.payflow.auth.api.AuthDtos.UserResponse;
import com.payflow.auth.domain.Role;
import com.payflow.auth.domain.User;
import com.payflow.auth.domain.UserRepository;
import com.payflow.auth.security.JwtService;
import com.payflow.shared.exception.BusinessException;
import com.payflow.shared.validation.CpfValidator;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("email_in_use", "E-mail já cadastrado");
        }
        User user = users.save(new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                CpfValidator.normalize(request.document()),
                Role.CUSTOMER));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
        JwtService.IssuedToken token = jwtService.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresAt());
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId)
                .map(AuthService::toResponse)
                .orElseThrow(() -> BusinessException.notFound("Usuário"));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}
