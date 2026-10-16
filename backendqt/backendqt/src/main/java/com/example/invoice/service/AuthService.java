package com.example.invoice.service;

import com.example.invoice.dto.auth.LoginRequest;
import com.example.invoice.dto.auth.LoginResponse;
import com.example.invoice.dto.auth.RegisterRequest;
import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.entity.User;
import com.example.invoice.repository.RoleRepository;
import com.example.invoice.repository.UserRepository;
import com.example.invoice.security.CustomUserDetailsService;
import com.example.invoice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final UserMapper userMapper;
	private final CustomUserDetailsService customUserDetailsService;

	public UserResponse register(RegisterRequest request) {
		throw new PublicRegistrationDisabledException();
	}

	/** Specific type lets the registration endpoint expose a code without changing global handlers. */
	public static class PublicRegistrationDisabledException extends org.springframework.security.access.AccessDeniedException {
		public PublicRegistrationDisabledException() {
			super("PUBLIC_REGISTRATION_DISABLED: Đăng ký công khai đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.");
		}
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		// Keep the authenticated password and token version from one locked database row.
		User user = userRepository.findByUsernameForAuthentication(request.username())
				.orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));
		authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
		UserDetails userDetails = customUserDetailsService.fromUser(user);
		return new LoginResponse(jwtService.generateToken(userDetails), "Bearer", userMapper.toResponse(user));
	}
}
