package com.example.invoice;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.*;
import com.example.invoice.exception.BadRequestException;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:workflow_concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class WorkflowConcurrencyIntegrationTest {
 @Autowired ReviewWorkflowService workflow; @Autowired DocumentRepository documents; @Autowired DocumentReviewRepository reviews;
 @PersistenceContext EntityManager em;
 @MockitoBean DocumentService scoped; @MockitoBean UserService users; @MockitoBean FinancialTransactionService transactions;
 @BeforeEach void setup(){when(scoped.lockForWorkflow(anyLong())).thenAnswer(i->{var d=documents.findByIdForUpdate(i.getArgument(0)).orElseThrow();em.refresh(d,LockModeType.PESSIMISTIC_WRITE);return d;});var role=new Role();role.setCode("ADMIN");var assignment=new UserRoleAssignment();assignment.setRole(role);var actor=new User();actor.setUserRoles(Set.of(assignment));when(users.loadCurrent(null)).thenReturn(actor);}
 @Test void simultaneousApprovalHasOneWinnerAndOneHistoryEntry() throws Exception {
  var role=new Role();role.setCode("ADMIN");var assignment=new UserRoleAssignment();assignment.setRole(role);
  var d=new Document();d.setOriginalFileName("test.png");d.setFileType("image/png");d.setFileSize(1L);d.setStatus(DocumentStatus.PROCESSED);d=documents.saveAndFlush(d);long id=d.getId();
  var realActor=new User(); realActor.setEmail("workflow@test.invalid"); realActor.setUsername("workflow-test"); realActor.setPassword("test-only"); realActor.setFullName("Test");
  realActor=userRepository.saveAndFlush(realActor); realActor.setUserRoles(Set.of(assignment)); when(users.loadCurrent(null)).thenReturn(realActor);
  var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
  try {Callable<Boolean> action=()->{barrier.await(10,TimeUnit.SECONDS);try{workflow.execute(id,"APPROVE",null,null);return true;}catch(BadRequestException expected){return false;}};
   var a=pool.submit(action);var b=pool.submit(action);assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
  } finally {pool.shutdownNow();}
  assertEquals(ReviewStatus.APPROVED,documents.findById(id).orElseThrow().getReviewStatus());assertEquals(1,reviews.findByDocumentId(id).size());verify(transactions,never()).createFromApprovedDocument(any(),any());
 }
 @Autowired UserRepository userRepository;
}
