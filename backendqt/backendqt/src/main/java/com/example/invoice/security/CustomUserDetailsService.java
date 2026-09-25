package com.example.invoice.security;

import com.example.invoice.entity.Permission;
import com.example.invoice.entity.Role;
import com.example.invoice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		com.example.invoice.entity.User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
		
		List<GrantedAuthority> authorities = new ArrayList<>();
		
		if (user.getRoles() != null && !user.getRoles().isEmpty()) {
			for (Role role : user.getRoles()) {
				if (role.isActive()) {
					authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
					if (role.getPermissions() != null) {
						for (Permission p : role.getPermissions()) {
							if (p.isActive()) {
								authorities.add(new SimpleGrantedAuthority("PERMISSION_" + p.getCode()));
							}
						}
					}
				}
			}
		} else {
			// Fallback to legacy enum if no DB roles are mapped yet
			authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
		}

		return org.springframework.security.core.userdetails.User
				.withUsername(user.getUsername())
				.password(user.getPassword())
				.authorities(authorities)
				.disabled(user.getStatus() != com.example.invoice.entity.UserStatus.ACTIVE)
				.build();
	}
}
