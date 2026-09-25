package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "role_id")
	private Long id;

	/** Unique machine-readable code (e.g. ADMIN, ACCOUNTANT). Used as the source of truth for Spring Security GrantedAuthority. */
	@Column(nullable = false, unique = true, length = 50)
	private String code;

	/** Human-readable display name. Maps to existing column "role_name" to preserve backward compatibility with the legacy schema. */
	@Column(name = "role_name", length = 100)
	private String name;

	private String description;

	@Column(nullable = false)
	private boolean active = true;

	@ManyToMany(mappedBy = "roles")
	private Set<User> users = new HashSet<>();

	@ManyToMany
	@JoinTable(
		name = "role_permissions",
		joinColumns = @JoinColumn(name = "role_id"),
		inverseJoinColumns = @JoinColumn(name = "permission_id")
	)
	private Set<Permission> permissions = new HashSet<>();

	@Column(updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
