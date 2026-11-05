package dev.paulo.orders_service.user.service;

import dev.paulo.orders_service.security.TokenService;
import dev.paulo.orders_service.user.domain.User;
import dev.paulo.orders_service.user.dto.LoginRequest;
import dev.paulo.orders_service.user.dto.RegisterUserRequest;
import dev.paulo.orders_service.user.dto.TokenResponse;
import dev.paulo.orders_service.user.dto.UserResponse;
import dev.paulo.orders_service.user.exception.InvalidCredentialsException;
import dev.paulo.orders_service.user.exception.UserAlreadyExistsException;
import dev.paulo.orders_service.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public UserResponse register(RegisterUserRequest userRequest) {

        String normalizedEmail = User.normalizeEmail(userRequest.email());

        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        String encodedPassword = passwordEncoder.encode(userRequest.password());

        User newUser;
        try {
            newUser = users.saveAndFlush(new User(normalizedEmail, encodedPassword));
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        return UserResponse.from(newUser);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {

        String email = User.normalizeEmail(request.email());

        User user = users.findByEmail(email)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        return new TokenResponse(tokens.issue(user));
    }
}
