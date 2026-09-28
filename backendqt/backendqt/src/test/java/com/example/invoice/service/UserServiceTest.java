package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.invoice.dto.user.ChangePasswordRequest;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.RoleRepository;
import com.example.invoice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
	@Mock private UserRepository userRepository;
	@Mock private RoleRepository roleRepository;
	@Mock private CompanyRepository companyRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private UserMapper userMapper;
	@InjectMocks private UserService userService;

	@Test
	void changePasswordRejectsIncorrectCurrentPassword() {
		User user = user("encoded-current");
		when(userRepository.findByUsername("employee")).thenReturn(java.util.Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "encoded-current")).thenReturn(false);

		assertThrows(BadRequestException.class, () -> userService.changePassword(authentication(), new ChangePasswordRequest("wrong-password", "NewPassword1")));

		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void changePasswordRejectsSamePassword() {
		User user = user("encoded-current");
		when(userRepository.findByUsername("employee")).thenReturn(java.util.Optional.of(user));
		when(passwordEncoder.matches("CurrentPassword1", "encoded-current")).thenReturn(true);

		assertThrows(BadRequestException.class, () -> userService.changePassword(authentication(), new ChangePasswordRequest("CurrentPassword1", "CurrentPassword1")));

		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void changePasswordStoresEncodedNewPassword() {
		User user = user("encoded-current");
		when(userRepository.findByUsername("employee")).thenReturn(java.util.Optional.of(user));
		when(passwordEncoder.matches("CurrentPassword1", "encoded-current")).thenReturn(true);
		when(passwordEncoder.matches("NewPassword1", "encoded-current")).thenReturn(false);
		when(passwordEncoder.encode("NewPassword1")).thenReturn("encoded-new");

		userService.changePassword(authentication(), new ChangePasswordRequest("CurrentPassword1", "NewPassword1"));

		verify(passwordEncoder).encode("NewPassword1");
		org.junit.jupiter.api.Assertions.assertEquals("encoded-new", user.getPassword());
	}

	private User user(String password) {
		User user = new User();
		user.setUsername("employee");
		user.setPassword(password);
		return user;
	}

	private UsernamePasswordAuthenticationToken authentication() {
		return new UsernamePasswordAuthenticationToken("employee", "unused");
	}
}
