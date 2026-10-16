package com.example.invoice.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/** Immutable authentication snapshot; token version is never inferred from credentials. */
public final class VersionedUserDetails extends org.springframework.security.core.userdetails.User {
	private final long tokenVersion;

	public VersionedUserDetails(String username, String password, boolean enabled,
			Collection<? extends GrantedAuthority> authorities, long tokenVersion) {
		super(username, password, enabled, true, true, true, authorities);
		if (tokenVersion < 0) throw new IllegalArgumentException("Invalid token version");
		this.tokenVersion = tokenVersion;
	}

	public long getTokenVersion() { return tokenVersion; }
}
