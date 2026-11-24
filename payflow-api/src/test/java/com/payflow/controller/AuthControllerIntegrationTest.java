package com.payflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.request.LoginRequest;
import com.payflow.dto.request.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/auth/register - should create user and return JWT token")
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com", "Password123!", "Alice Smith");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.expiresIn").value(greaterThan(0)));
    }

    @Test
    @DisplayName("POST /api/auth/register - should return 409 when email already registered")
    void register_DuplicateEmail_Returns409() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "duplicate@example.com", "Password123!", "Dup User");

        // Register first time
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Register again with same email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already in use")));
    }

    @Test
    @DisplayName("POST /api/auth/register - should return 400 for invalid email format")
    void register_InvalidEmail_Returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "not-an-email", "Password123!", "Bad Email User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/register - should return 400 when password is too short")
    void register_ShortPassword_Returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "user@example.com", "short", "User Name");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login - should authenticate and return JWT token")
    void login_Success() throws Exception {
        // First register
        RegisterRequest reg = new RegisterRequest(
                "bob@example.com", "Password123!", "Bob Jones");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Then login
        LoginRequest login = new LoginRequest("bob@example.com", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - should return 401 for wrong password")
    void login_WrongPassword_Returns401() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "carol@example.com", "RightPassword1!", "Carol");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("carol@example.com", "WrongPassword1!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login - should return 401 for non-existent user")
    void login_NonExistentUser_Returns401() throws Exception {
        LoginRequest login = new LoginRequest("nobody@example.com", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }
}
