package com.example.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Keeps the raw value so invalid configuration can be reported on demand. */
@Component
@ConfigurationProperties(prefix = "app.internal-company")
public class InternalCompanyProperties {
	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
