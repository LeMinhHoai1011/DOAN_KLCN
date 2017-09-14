package com.example.invoice.dto.auth;

import com.example.invoice.dto.user.UserResponse;

public record LoginResponse(String token, String tokenType, UserResponse user) {
}
