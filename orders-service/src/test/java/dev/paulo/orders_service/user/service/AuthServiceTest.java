package dev.paulo.orders_service.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.paulo.orders_service.user.domain.User;
import dev.paulo.orders_service.user.dto.RegisterUserRequest;
import dev.paulo.orders_service.user.dto.UserResponse;
import dev.paulo.orders_service.user.exception.UserAlreadyExistsException;
import dev.paulo.orders_service.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService service;

    @Test
    void register_savesPasswordHashAndNormalizedEmail() {
        when(passwordEncoder.encode("segredo123")).thenReturn("hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.register(new RegisterUserRequest("Ana@x.pt", "segredo123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(response.email()).isEqualTo("ana@x.pt");
    }

    @Test
    void register_withExistingEmail_throwsAndDoesNotSave() {
        when(users.findByEmail("ana@x.pt")).thenReturn(Optional.of(new User("ana@x.pt", "hash")));

        assertThatThrownBy(() -> service.register(new RegisterUserRequest("Ana@x.pt", "segredo123")))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void register_whenDatabaseRejectsDuplicate_throwsUserAlreadyExists() {
        when(passwordEncoder.encode("segredo123")).thenReturn("hash");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.register(new RegisterUserRequest("Ana@x.pt", "segredo123")))
                .isInstanceOf(UserAlreadyExistsException.class);
    }
}
