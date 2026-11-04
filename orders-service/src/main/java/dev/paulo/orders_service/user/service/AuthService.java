package dev.paulo.orders_service.user.service;

import dev.paulo.orders_service.user.domain.User;
import dev.paulo.orders_service.user.dto.RegisterUserRequest;
import dev.paulo.orders_service.user.dto.UserResponse;
import dev.paulo.orders_service.user.exception.UserAlreadyExistsException;
import dev.paulo.orders_service.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterUserRequest userRequest) {

        String normalizedEmail = User.normalizeEmail(userRequest.email());

        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        String encodedPassword = passwordEncoder.encode(userRequest.password());

        UUID userId = UUID.randomUUID();

        Instant createdAt = Instant.now();

        User newUser = new User(userRequest.email(), encodedPassword);

        users.save(newUser);

        return UserResponse.from(newUser);
    }
}
