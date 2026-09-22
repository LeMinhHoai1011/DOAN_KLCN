package com.example.invoice.entity;

public enum UserRole {
	ADMIN,
	ACCOUNTANT,
	EMPLOYEE,
	/**
	 * Legacy value kept so existing rows can be read safely. It is treated as EMPLOYEE by the API.
	 */
	USER
}
