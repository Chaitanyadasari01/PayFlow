package com.payflow.dto.response;

public record AuthResponse(
    String token,
    String tokenType,
    long expiresIn,
    String email,
    String fullName
) {
    public AuthResponse(String token, long expiresIn, String email, String fullName) {
        this(token, "Bearer", expiresIn, email, fullName);
    }
}
