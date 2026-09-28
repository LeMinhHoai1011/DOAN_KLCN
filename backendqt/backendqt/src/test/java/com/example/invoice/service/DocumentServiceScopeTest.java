package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.invoice.entity.*;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.*;
import io.minio.MinioClient;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DocumentServiceScopeTest {
 @Mock DocumentRepository documents; @Mock DocumentTypeRepository types; @Mock DocumentVersionRepository versions; @Mock UserService users; @Mock StorageService storage; @Mock MinioClient minio;
 @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
 private DocumentService service(User actor) { SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("actor", "x")); when(users.loadCurrent(any())).thenReturn(actor); return new DocumentService(documents,types,versions,users,storage,minio); }
 private User user(long id,String role,long company) { Role r=new Role(); r.setCode(role); UserRoleAssignment a=new UserRoleAssignment(); a.setRole(r); User u=new User(); u.setId(id); Company c=new Company(); c.setId(company); u.setCompany(c); u.setUserRoles(Set.of(a)); return u; }
 @Test void employeeCannotLoadAnotherOwnersDocumentButCanLoadOwn() { User employee=user(1,"EMPLOYEE",1); Document own=new Document(); own.setId(8L); when(documents.findByIdAndUploadedById(8L,1L)).thenReturn(Optional.of(own)); assertSame(own,service(employee).load(8L)); when(documents.findByIdAndUploadedById(9L,1L)).thenReturn(Optional.empty()); assertThrows(ResourceNotFoundException.class,()->service(employee).load(9L)); verify(documents,never()).findById(9L); }
 @Test void accountantCannotLoadOtherCompanyButCanLoadOwnCompany() { User accountant=user(2,"ACCOUNTANT",10); Document own=new Document(); own.setId(8L); when(documents.findByIdAndCompanyId(8L,10L)).thenReturn(Optional.of(own)); assertSame(own,service(accountant).load(8L)); when(documents.findByIdAndCompanyId(9L,10L)).thenReturn(Optional.empty()); assertThrows(ResourceNotFoundException.class,()->service(accountant).load(9L)); verify(documents,never()).findById(9L); }
}
