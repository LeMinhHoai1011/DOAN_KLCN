package com.example.invoice.service;

import com.example.invoice.dto.auth.LoginRequest;
import com.example.invoice.dto.auth.LoginResponse;
import com.example.invoice.dto.auth.RegisterRequest;
import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
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

	@Transactional
	public UserResponse register(RegisterRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new BadRequestException("Username already exists");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new BadRequestException("Email already exists");
		}

		User user = new User();
		user.setUsername(request.username());
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setFullName(request.fullName());
		user.setEmail(request.email());
		user.setPhone(request.phone());
		user.setRole(com.example.invoice.entity.UserRole.EMPLOYEE); // Keep legacy enum
		
		roleRepository.findByCode("EMPLOYEE").ifPresent(r -> user.getRoles().add(r)); // Map DB role

		return userMapper.toResponse(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
		User user = userRepository.findByUsername(request.username()).orElseThrow();
		UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getUsername());
		return new LoginResponse(jwtService.generateToken(userDetails), "Bearer", userMapper.toResponse(user));
	}
}
