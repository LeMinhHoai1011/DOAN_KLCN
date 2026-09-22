package com.example.invoice.controller;

import com.example.invoice.dto.user.ChangePasswordRequest;
import com.example.invoice.dto.user.AdminCreateUserRequest;
import com.example.invoice.dto.user.AdminUpdateUserRequest;
import com.example.invoice.dto.user.UpdateUserRequest;
import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
	private final UserService userService;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<UserResponse> findAll() {
		return userService.findAll();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse create(@Valid @RequestBody AdminCreateUserRequest request) {
		return userService.createByAdmin(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest request) {
		return userService.updateByAdmin(id, request);
	}

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public UserResponse me(Authentication authentication) {
		return userService.currentUser(authentication);
	}

	@PutMapping("/me")
	public UserResponse update(Authentication authentication, @Valid @RequestBody UpdateUserRequest request) {
		return userService.updateCurrent(authentication, request);
	}
	

	@PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(authentication, request);
	}
}
