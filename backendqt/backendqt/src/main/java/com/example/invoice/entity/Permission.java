package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "permission_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id", nullable = false)
	private PermissionGroup group;

	@Column(nullable = false, unique = true, length = 100)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	private String description;

	@Column(nullable = false)
	private boolean active = true;

	@ManyToMany(mappedBy = "permissions")
	private Set<Role> roles = new HashSet<>();
}
