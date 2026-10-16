package com.example.invoice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
	private final SecretKey secretKey;
	private final long expirationMs;

	public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-ms}") long expirationMs) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
	}

	public String generateToken(UserDetails userDetails) {
		if (!(userDetails instanceof VersionedUserDetails versioned)) {
			throw new IllegalArgumentException("Token version is required");
		}
		Date now = new Date();
		return Jwts.builder()
				.subject(userDetails.getUsername())
				.claim("tv", versioned.getTokenVersion())
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMs))
				.signWith(secretKey)
				.compact();
	}

	public String extractUsername(String token) {
		Claims payload = claims(token);
		tokenVersion(payload);
		return payload.getSubject();
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		Claims payload = claims(token);
		return userDetails instanceof VersionedUserDetails versioned
				&& tokenVersion(payload) == versioned.getTokenVersion()
				&& userDetails.getUsername().equals(payload.getSubject())
				&& payload.getExpiration() != null && payload.getExpiration().after(new Date());
	}

	private long tokenVersion(Claims payload) {
		Object value = payload.get("tv");
		if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) {
			throw new io.jsonwebtoken.MalformedJwtException("Invalid or missing token version");
		}
		return ((Number) value).longValue();
	}

	private Claims claims(String token) {
		return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
	}
}
