package com.example.invoice.service;

import com.example.invoice.dto.user.ChangePasswordRequest;
import com.example.invoice.dto.user.AdminCreateUserRequest;
import com.example.invoice.dto.user.AdminUpdateUserRequest;
import com.example.invoice.dto.user.UpdateUserRequest;
import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;

	public UserResponse currentUser(Authentication authentication) {
		return userMapper.toResponse(loadCurrent(authentication));
	}

	public List<UserResponse> findAll() {
		return userRepository.findAll().stream().map(userMapper::toResponse).toList();
	}

	@Transactional
	public UserResponse createByAdmin(AdminCreateUserRequest request) {
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
		user.setRole(request.role());
		return userMapper.toResponse(userRepository.save(user));
	}

	@Transactional
	public UserResponse updateByAdmin(Long id, AdminUpdateUserRequest request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		user.setRole(request.role());
		user.setStatus(request.status());
		return userMapper.toResponse(user);
	}

	@Transactional
	public UserResponse updateCurrent(Authentication authentication, UpdateUserRequest request) {
		User user = loadCurrent(authentication);
		if (request.fullName() != null) user.setFullName(request.fullName());
		if (request.email() != null) user.setEmail(request.email());
		if (request.phone() != null) user.setPhone(request.phone());
		if (request.avatar() != null) user.setAvatar(request.avatar());
		return userMapper.toResponse(user);
	}

	@Transactional
	public void changePassword(Authentication authentication, ChangePasswordRequest request) {
		User user = loadCurrent(authentication);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new BadRequestException("Current password is incorrect");
		}
		user.setPassword(passwordEncoder.encode(request.newPassword()));
	}

	User loadCurrent(Authentication authentication) {
		return userRepository.findByUsername(authentication.getName())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}
}
