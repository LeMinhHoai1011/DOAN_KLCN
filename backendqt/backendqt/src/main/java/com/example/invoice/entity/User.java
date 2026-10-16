package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
	@Setter(lombok.AccessLevel.NONE)
	@Column(name = "token_version", nullable = false)
	private Long tokenVersion = 0L;

	@Setter(lombok.AccessLevel.NONE)
	@jakarta.persistence.Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion;

	public void revokeTokens() {
		if (tokenVersion == null || tokenVersion < 0 || tokenVersion == Long.MAX_VALUE) {
			throw new com.example.invoice.exception.BadRequestException("TOKEN_VERSION_EXHAUSTED: Không thể tăng phiên bản xác thực");
		}
		tokenVersion++;
	}
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id")
	private Company company;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<UserRoleAssignment> userRoles = new HashSet<>();

	@Transient
	private Set<Role> roles = new HashSet<>();

	@Column(nullable = false, unique = true, length = 100)
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String password;

	@Column(nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false, unique = true, length = 150)
	private String email;

	private String phone;
	private String avatar;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	void prePersist() {
		validateTokenVersion();
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void preUpdate() {
		validateTokenVersion();
		updatedAt = LocalDateTime.now();
	}

	private void validateTokenVersion() {
		if (tokenVersion == null || tokenVersion < 0) throw new IllegalStateException("Invalid token version");
	}
}
