package com.example.invoice;

import com.example.invoice.dto.accounting.AccountingCategoryRequest;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.AccountingCategoryService;
import com.example.invoice.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:category_nullable_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(com.example.invoice.support.IsolatedAccountFixtures.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) @Transactional
class AccountingCategoryIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 @Autowired AccountingCategoryRepository categories; @Autowired CompanyRepository companies; @Autowired UserRepository users;
 @Autowired FinancialTransactionRepository transactions; @Autowired AccountingCategoryService service; @Autowired PlatformTransactionManager manager;
 @BeforeAll void baseline() {
   assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->c.getMetaData().getURL().startsWith("jdbc:h2:mem:")));
   new TransactionTemplate(manager).executeWithoutResult(status->{
     for(long id:new long[]{1,13,99})jdbc.update("insert into companies(company_id,company_name) values (?,?)",id,"Isolated company "+id);
     Company company=companies.findById(13L).orElseThrow();
     for(String code:List.of("SALE","PURCHASE","SERVICE","OFFICE")){var c=new AccountingCategory();c.setCompany(company);c.setCategoryCode(code);c.setCategoryName(code);categories.save(c);}
     for(String name:List.of("admin","accountant","employee")){var user=users.findByUsername(name).orElseThrow();user.setCompany(company);users.save(user);}
   });
 }
 String token(String username)throws Exception {
   var response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",username,"password",username.equals("admin")?"Admin@123456":username.equals("accountant")?"Accountant@123":"Employee@123")))).andExpect(status().isOk()).andReturn();
   return "Bearer "+json.readTree(response.getResponse().getContentAsString()).get("token").asText();
 }
 AccountingCategory category(){return categories.findByCompanyIdAndCategoryCodeAndActiveTrue(13L,"PURCHASE").orElseThrow();}
 AccountingCategoryRequest request(String code,AccountingCategoryType type,boolean active,Long company){return new AccountingCategoryRequest(code,"Explicit user choice",null,type,active,company);}
 @Test void company13HasFourActiveLegacyCategoriesAndNullNeverInfersType()throws Exception {
   for(String user:List.of("admin","accountant")) {
     var response=mvc.perform(get("/api/v1/accounting-categories").header("Authorization",token(user))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4)).andReturn();
     var list=json.readTree(response.getResponse().getContentAsString());for(var item:list){assertEquals(13,item.get("companyId").asLong());assertTrue(item.get("categoryType").isNull());}
   }
   mvc.perform(get("/api/v1/accounting-categories").param("companyId","13").param("type","INCOME").header("Authorization",token("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
 }
 @Test void unauthorizedForbiddenMissingCompanyAndRealEmptyHaveDifferentHttpResults()throws Exception {
   mvc.perform(get("/api/v1/accounting-categories")).andExpect(status().isUnauthorized());
   mvc.perform(get("/api/v1/accounting-categories").header("Authorization",token("employee"))).andExpect(status().isForbidden());
   mvc.perform(get("/api/v1/accounting-categories").param("companyId","1").header("Authorization",token("accountant"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
   mvc.perform(get("/api/v1/accounting-categories").param("companyId","1").header("Authorization",token("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
   mvc.perform(get("/api/v1/accounting-categories").param("companyId","999999").header("Authorization",token("admin"))).andExpect(status().isNotFound());
 }
 @Test void legacyNullRemainsNullThroughRenameAndDeactivationWithoutCreatingTransactions()throws Exception {
   var c=category();long before=transactions.count();String admin=token("admin");
   mvc.perform(put("/api/v1/accounting-categories/"+c.getId()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("PURCHASE",null,false,13L)))).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
   assertNull(categories.findById(c.getId()).orElseThrow().getCategoryType());
   mvc.perform(get("/api/v1/accounting-categories").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
   mvc.perform(get("/api/v1/accounting-categories").param("includeInactive","true").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
   assertEquals(before,transactions.count());
 }
 @Test void creationRequiresExplicitValidTypeAndDuplicateCodeIsCompanyScoped()throws Exception {
   String admin=token("admin");long before=transactions.count();
   mvc.perform(post("/api/v1/accounting-categories").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("NEW_CATEGORY",AccountingCategoryType.EXPENSE,true,13L)))).andExpect(status().isCreated()).andExpect(jsonPath("$.categoryType").value("EXPENSE"));
   assertEquals(before,transactions.count());
   mvc.perform(post("/api/v1/accounting-categories").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("sale",AccountingCategoryType.INCOME,true,13L)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("DUPLICATE_CATEGORY_CODE"));
   mvc.perform(post("/api/v1/accounting-categories").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("SALE",AccountingCategoryType.INCOME,true,1L)))).andExpect(status().isCreated()).andExpect(jsonPath("$.companyId").value(1));
   mvc.perform(post("/api/v1/accounting-categories").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("UNTYPED_NEW",null,true,13L)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("CATEGORY_TYPE_REQUIRED"));
 }
 @Test void invalidTypeAndUnauthorizedMutationNeverWriteCategories()throws Exception {
   long before=categories.count();String payload=json.writeValueAsString(request("NEW",AccountingCategoryType.INCOME,true,13L));
   mvc.perform(post("/api/v1/accounting-categories").header("Authorization",token("accountant")).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/accounting-categories").header("Authorization",token("admin")).contentType(MediaType.APPLICATION_JSON).content(payload.replace("INCOME","SALE"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_ACCOUNTING_CATEGORY"));
   mvc.perform(get("/api/v1/accounting-categories").header("Authorization",token("admin")).param("type","PURCHASE")).andExpect(status().isBadRequest());assertEquals(before,categories.count());
 }
 @Test void referencedCategoryKeepsLinksAndAllowsDeactivationButNotCodeOrConflictingType()throws Exception {
   var c=category();var t=new FinancialTransaction();t.setCompany(c.getCompany());t.setAccountingCategory(c);t.setTransactionType("EXPENSE");t.setAmount(BigDecimal.ONE);t.setTransactionDate(LocalDate.of(2026,10,16));t.setStatus("ACTIVE");t=transactions.saveAndFlush(t);long id=t.getId();String admin=token("admin");
   mvc.perform(put("/api/v1/accounting-categories/"+c.getId()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("OTHER_CODE",null,true,13L)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("CATEGORY_IN_USE"));
   mvc.perform(put("/api/v1/accounting-categories/"+c.getId()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("PURCHASE",AccountingCategoryType.INCOME,true,13L)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("CATEGORY_TYPE_CONFLICT"));
   mvc.perform(put("/api/v1/accounting-categories/"+c.getId()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("PURCHASE",null,false,13L)))).andExpect(status().isOk());
   assertEquals(c.getId(),transactions.findById(id).orElseThrow().getAccountingCategory().getId());assertNull(categories.findById(c.getId()).orElseThrow().getCategoryType());assertEquals(1,transactions.count());
 }
 @Test void explicitTypeChoiceDoesNotRewriteOtherCategoriesOrCreateFinancialTransactions()throws Exception {
   var c=category();long before=transactions.count();
   mvc.perform(put("/api/v1/accounting-categories/"+c.getId()).header("Authorization",token("admin")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("PURCHASE",AccountingCategoryType.INCOME,true,13L)))).andExpect(status().isOk()).andExpect(jsonPath("$.categoryType").value("INCOME"));
   assertEquals(3,categories.findByCompanyId(13L).stream().filter(x->x.getCategoryType()==null).count());assertEquals(before,transactions.count());
 }
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 void concurrentCreatesWithinSameCompanyHaveOnlyOneWinner()throws Exception {
   var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);try{
     Callable<Boolean> create=()->{barrier.await(10,TimeUnit.SECONDS);try{service.create(request("CONCURRENT",AccountingCategoryType.EXPENSE,true,99L),new TestingAuthenticationToken("admin","unused","ROLE_ADMIN"));return true;}catch(BadRequestException e){assertTrue(e.getMessage().startsWith("DUPLICATE_CATEGORY_CODE"));return false;}};
     var a=pool.submit(create);var b=pool.submit(create);assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));assertEquals(1,categories.findByCompanyId(99L).size());
   }finally{pool.shutdownNow();categories.deleteAll(categories.findByCompanyId(99L));}
 }

 @Test void adminWithoutCompanyMustChooseCompanyRatherThanReceivingFakeEmpty()throws Exception {
   users.findByUsername("admin").orElseThrow().setCompany(null);users.flush();String admin=token("admin");
   mvc.perform(get("/api/v1/accounting-categories").header("Authorization",admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("COMPANY_REQUIRED"));
   mvc.perform(get("/api/v1/accounting-categories").param("companyId","13").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
 }
 @Test void omittedTypeKeepsExplicitTypeAndValidationErrorsDoNotWrite()throws Exception {
   var c=category();c.setCategoryType(AccountingCategoryType.EXPENSE);categories.flush();String admin=token("admin");
   mvc.perform(put("/api/v1/accounting-categories/"+c.getId()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("PURCHASE",null,true,13L)))).andExpect(status().isOk()).andExpect(jsonPath("$.categoryType").value("EXPENSE"));
   long before=categories.count();
   mvc.perform(post("/api/v1/accounting-categories").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("INVALID-CODE",AccountingCategoryType.INCOME,true,13L)))).andExpect(status().isBadRequest());
   assertEquals(before,categories.count());assertEquals(AccountingCategoryType.EXPENSE,categories.findById(c.getId()).orElseThrow().getCategoryType());
 }
}
