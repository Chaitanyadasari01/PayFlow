package com.payflow.service;

import com.payflow.dto.request.LoginRequest;
import com.payflow.dto.request.RegisterRequest;
import com.payflow.dto.response.AuthResponse;
import com.payflow.entity.User;
import com.payflow.exception.DuplicateResourceException;
import com.payflow.repository.UserRepository;
import com.payflow.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider, authenticationManager);
    }

    @Test
    @DisplayName("Should register new user successfully and return JWT token")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "Password123!", "Alice Smith");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed-pwd");

        User savedUser = new User("alice@example.com", "hashed-pwd", "Alice Smith");
        savedUser.setId(10L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateToken("alice@example.com", 10L)).thenReturn("mocked-jwt-token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mocked-jwt-token");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.fullName()).isEqualTo("Alice Smith");

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email is already registered")
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "Password123!", "Existing User");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already in use");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void login_Success() {
        LoginRequest request = new LoginRequest("bob@example.com", "secret123");

        User user = new User("bob@example.com", "hashed-secret", "Bob Jones");
        user.setId(20L);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(org.springframework.security.core.Authentication.class));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken("bob@example.com", 20L)).thenReturn("bob-jwt-token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("bob-jwt-token");
        assertThat(response.email()).isEqualTo("bob@example.com");
        assertThat(response.fullName()).isEqualTo("Bob Jones");
    }

    @Test
    @DisplayName("Should throw BadCredentialsException on invalid credentials")
    void login_BadCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest("bob@example.com", "wrong-password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }
}
