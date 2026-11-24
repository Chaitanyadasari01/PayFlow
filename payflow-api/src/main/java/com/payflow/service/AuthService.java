package com.payflow.service;

import com.payflow.dto.request.LoginRequest;
import com.payflow.dto.request.RegisterRequest;
import com.payflow.dto.response.AuthResponse;
import com.payflow.entity.User;
import com.payflow.exception.DuplicateResourceException;
import com.payflow.repository.UserRepository;
import com.payflow.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already in use: " + request.email());
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName()
        );

        User savedUser = userRepository.save(user);
        String token = tokenProvider.generateToken(savedUser.getEmail(), savedUser.getId());

        return new AuthResponse(token, tokenProvider.getExpirationMs(), savedUser.getEmail(), savedUser.getFullName());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String token = tokenProvider.generateToken(user.getEmail(), user.getId());

        return new AuthResponse(token, tokenProvider.getExpirationMs(), user.getEmail(), user.getFullName());
    }
}
