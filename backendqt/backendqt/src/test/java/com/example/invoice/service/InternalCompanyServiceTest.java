package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.invoice.config.InternalCompanyProperties;
import com.example.invoice.entity.Company;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.CompanyRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalCompanyServiceTest {
	@Mock private CompanyRepository repository;
	private InternalCompanyProperties properties;
	private InternalCompanyService service;

	@BeforeEach
	void setUp() {
		properties = new InternalCompanyProperties();
		service = new InternalCompanyService(properties, repository);
	}

	@Test
	void resolvesOnlyConfiguredCompanyWithoutWriting() {
		properties.setId(" 42 ");
		Company company = new Company();
		company.setId(42L);
		when(repository.findById(42L)).thenReturn(Optional.of(company));
		assertTrue(service.isConfigured());
		verifyNoInteractions(repository);
		assertSame(company, service.resolveRequired());
		verify(repository).findById(42L);
		verifyNoMoreInteractions(repository);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t"})
	void missingIdFailsOnlyWhenRequired(String id) {
		properties.setId(id);
		assertFalse(service.isConfigured());
		assertCode("INTERNAL_COMPANY_NOT_CONFIGURED");
		verifyNoInteractions(repository);
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "abc", "1.5", "+42", "9223372036854775808"})
	void invalidIdNeverQueriesOrFallsBack(String id) {
		properties.setId(id);
		assertFalse(service.isConfigured());
		assertCode("INTERNAL_COMPANY_INVALID_CONFIG");
		verifyNoInteractions(repository);
	}

	@Test
	void missingCompanyNeverFallsBackToLegacyCompanies() {
		properties.setId("42");
		when(repository.findById(42L)).thenReturn(Optional.empty());
		assertTrue(service.isConfigured());
		assertCode("INTERNAL_COMPANY_NOT_FOUND");
		verify(repository).findById(42L);
		verifyNoMoreInteractions(repository);
	}

	@Test
	void resolvesAgainInsteadOfCachingAnEntity() {
		properties.setId("42");
		when(repository.findById(42L)).thenReturn(Optional.of(new Company()), Optional.empty());
		assertNotNull(service.resolveRequired());
		assertCode("INTERNAL_COMPANY_NOT_FOUND");
		verify(repository, times(2)).findById(42L);
		verifyNoMoreInteractions(repository);
	}

	private void assertCode(String code) {
		BadRequestException exception = assertThrows(BadRequestException.class, service::resolveRequired);
		assertTrue(exception.getMessage().startsWith(code + ":"));
		assertFalse(exception.getMessage().contains("9223372036854775808"));
	}
}
