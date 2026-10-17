package com.example.invoice;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.*;
import com.example.invoice.dto.accounting.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
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
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:payments_v1_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(com.example.invoice.support.IsolatedAccountFixtures.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) @Transactional
class PaymentIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
 @Autowired DocumentRepository documents; @Autowired InvoiceRepository invoices; @Autowired CompanyRepository companies; @Autowired UserRepository users;
 @Autowired FinancialTransactionRepository transactions; @Autowired AuditLogRepository audit; @Autowired AccountingCategoryRepository categories;
 @Autowired FinancialTransactionService service; @Autowired RoleRepository roles;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PaymentSchemaGuard paymentSchema;
 @Autowired PaymentLinkService paymentLinks;
 @BeforeAll void baseline(){assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->c.getMetaData().getURL().startsWith("jdbc:h2:mem:")));
 new TransactionTemplate(manager).executeWithoutResult(s->{for(long id:new long[]{1,13})jdbc.update("insert into companies(company_id,company_name) values (?,?)",id,"Isolated "+id);for(String name:List.of("admin","accountant","employee")){var u=users.findByUsername(name).orElseThrow();u.setCompany(companies.findById(13L).orElseThrow());}});
 jdbc.execute("create unique index test_payment_request on financial_transactions(company_id,request_key)");}
 Invoice invoice(long company,boolean approved){var d=new Document();d.setCompany(companies.findById(company).orElseThrow());d.setOriginalFileName("payment-"+UUID.randomUUID()+".pdf");d.setFileType("application/pdf");d.setFileSize(1L);d.setStatus(approved?DocumentStatus.COMPLETED:DocumentStatus.PROCESSED);d.setReviewStatus(approved?ReviewStatus.APPROVED:ReviewStatus.PENDING);d.setTransactionAssessmentType("EXPENSE");d=documents.saveAndFlush(d);var i=new Invoice();i.setDocument(d);i.setInvoiceNumber("INV-"+d.getId());i.setTotalAmount(new BigDecimal("100.00"));i.setInvoiceDate(LocalDate.of(2026,10,16));return invoices.saveAndFlush(i);}
 String token(String name)throws Exception{var result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password",name.equals("admin")?"Admin@123456":name.equals("employee")?"Employee@123":"Accountant@123")))).andExpect(status().isOk()).andReturn();return "Bearer "+json.readTree(result.getResponse().getContentAsString()).get("token").asText();}
 Map<String,Object> body(Invoice i,String amount){var r=new LinkedHashMap<String,Object>();r.put("transactionType","EXPENSE");r.put("amount",amount);r.put("transactionDate","2026-10-16");r.put("paymentMethod","BANK_TRANSFER");r.put("paymentConfirmed",true);r.put("requestKey",UUID.randomUUID().toString());if(i!=null)r.put("invoiceId",i.getId());return r;}
 JsonNode create(Map<String,Object> request,String token)throws Exception{return json.readTree(mvc.perform(post("/api/v1/financial-transactions").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 JsonNode summary(Invoice invoice,String token)throws Exception{return json.readTree(mvc.perform(get("/api/v1/financial-transactions/payment-summary").param("invoiceId",invoice.getId().toString()).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void invoiceCanReceivePartialThenFullPaymentsAndUnlinkedCashIsSeparate()throws Exception{var i=invoice(13,true);String auth=token("accountant");var first=create(body(i,"40.00"),auth);assertEquals(i.getDocument().getId(),first.get("documentId").asLong());assertEquals(i.getId(),first.get("invoiceId").asLong());var partial=summary(i,auth);assertEquals("PARTIALLY_PAID",partial.get("paymentStatus").asText());assertEquals(60,partial.get("remaining").asInt());create(body(i,"60.00"),auth);assertEquals("PAID",summary(i,auth).get("paymentStatus").asText());
 mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(i,"0.01")))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_EXCEEDS_TOTAL"));create(body(null,"5.00"),auth);assertEquals(100,summary(i,auth).get("confirmedPaid").asInt());}
 @Test void retriesReturnSameTransactionAndChangedPayloadWithSameKeyIsRejected()throws Exception{var i=invoice(13,true);var request=body(i,"40.00");String auth=token("accountant");var first=create(request,auth);assertEquals(first.get("id"),create(request,auth).get("id"));assertEquals(1,transactions.linked(13L,i.getDocument().getId(),i.getId()).size());assertEquals(1,audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("FINANCIAL_TRANSACTION",first.get("id").asText()).size());request.put("amount","41.00");mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));}
 @Test void wrongCompanyWrongPairMissingInvoiceAndUnapprovedDocumentsAreRejected()throws Exception{var own=invoice(13,true);var otherOwn=invoice(13,true);var foreign=invoice(1,true);var pending=invoice(13,false);String auth=token("accountant");
 for(Invoice invalid:List.of(foreign)){mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(invalid,"1.00")))).andExpect(status().isNotFound());}
 var mismatch=body(own,"1.00");mismatch.put("documentId",otherOwn.getDocument().getId());mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(mismatch))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVOICE_DOCUMENT_MISMATCH"));
 var missing=body(null,"1.00");missing.put("invoiceId",9999999);mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(missing))).andExpect(status().isNotFound());
 mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(pending,"1.00")))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("DOCUMENT_NOT_PAYABLE"));assertEquals(0,transactions.count());}
 @Test void invalidAmountDirectionMethodConfirmationAndPermissionsDoNotWrite()throws Exception{String auth=token("accountant");for(var mutation:List.of(Map.of("amount","0"),Map.of("amount","1.001"),Map.of("transactionType","TRANSFER"),Map.of("paymentMethod","GUESS"),Map.of("paymentConfirmed",false),Map.of("requestKey","bad-key"))){var request=body(null,"1.00");request.putAll(mutation);mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isBadRequest());}
 mvc.perform(post("/api/v1/financial-transactions").header("Authorization",token("employee")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(null,"1.00")))).andExpect(status().isForbidden());assertEquals(0,transactions.count());}
 @Test void editCancelAndRetryKeepAuditAndRecomputeRemaining()throws Exception{var i=invoice(13,true);String auth=token("accountant");var originalRequest=body(i,"40.00");var original=create(originalRequest,auth);var edit=body(i,"30.00");edit.put("expectedUpdatedAt",original.get("updatedAt").asText());String path="/api/v1/financial-transactions/"+original.get("id").asLong();
 var updated=json.readTree(mvc.perform(put(path).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertEquals(70,summary(i,auth).get("remaining").asInt());
 mvc.perform(put(path).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit))).andExpect(status().isConflict());assertEquals(30,create(originalRequest,auth).get("amount").asInt());
 var cancellation=Map.of("reason","Duplicate bank entry corrected by accountant","expectedUpdatedAt",updated.get("updatedAt").asText());for(int n=0;n<2;n++)mvc.perform(post(path+"/cancel").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cancellation))).andExpect(status().isOk()).andExpect(jsonPath("$.paymentConfirmation").value("CANCELLED"));
 assertEquals("UNPAID",summary(i,auth).get("paymentStatus").asText());assertEquals(1,transactions.count());assertEquals(3,audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("FINANCIAL_TRANSACTION",original.get("id").asText()).size());assertEquals("CANCELLED",create(originalRequest,auth).get("paymentConfirmation").asText());create(body(i,"100.00"),auth);assertEquals("PAID",summary(i,auth).get("paymentStatus").asText());mvc.perform(delete(path).header("Authorization",auth)).andExpect(status().isBadRequest());}
 @Test void legacyRecordsAreUnverifiedReadOnlyAndNeverBecomePaidOrCashTotals()throws Exception{var i=invoice(13,true);var old=new FinancialTransaction();old.setCompany(i.getDocument().getCompany());old.setInvoice(i);old.setTransactionType("EXPENSE");old.setAmount(new BigDecimal("100.00"));old.setTransactionDate(LocalDate.of(2026,10,16));old.setStatus("COMPLETED");old=transactions.saveAndFlush(old);String auth=token("accountant");var before=summary(i,auth);assertTrue(before.get("paymentStatus").isNull());assertEquals(0,before.get("confirmedPaid").asInt());assertEquals(1,before.get("unverifiedCount").asInt());create(body(i,"40.00"),auth);assertEquals(40,summary(i,auth).get("confirmedPaid").asInt());
 mvc.perform(get("/api/v1/financial-transactions/unverified").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
 mvc.perform(get("/api/v1/dashboard/financial").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.totalExpense").value(40)).andExpect(jsonPath("$.expensesByCategory[0].amount").value(40)).andExpect(jsonPath("$.unverifiedTransactionCount").value(1));
 var export=mvc.perform(get("/api/v1/exports/financial-report").header("Authorization",auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
 try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(export))){assertEquals(40,book.getSheet("Tong quan").getRow(1).getCell(3).getNumericCellValue());assertEquals(1,book.getSheet("Khong tinh thanh toan").getLastRowNum());}
 var edit=body(i,"20.00");edit.put("expectedUpdatedAt",old.getUpdatedAt().toString());mvc.perform(put("/api/v1/financial-transactions/"+old.getId()).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit))).andExpect(status().isConflict());assertNull(transactions.findById(old.getId()).orElseThrow().getPaymentConfirmation());assertEquals("COMPLETED",old.getStatus());}
 @Test void reconciliationAppendsSnapshotsAndGetDoesNotWrite()throws Exception{var i=invoice(13,true);String auth=token("accountant");create(body(i,"40.00"),auth);String path="/api/v1/financial-transactions/payment-reconciliation";
 mvc.perform(post(path).param("invoiceId",i.getId().toString()).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.comparison").value("UNDERPAID"));var first=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("PAYMENT_RECONCILIATION",i.getDocument().getId().toString()).getFirst().getNewValue();long auditBefore=audit.count();summary(i,auth);mvc.perform(get(path+"/history").param("invoiceId",i.getId().toString()).header("Authorization",auth)).andExpect(status().isOk());assertEquals(auditBefore,audit.count());create(body(i,"60.00"),auth);
 mvc.perform(post(path).param("invoiceId",i.getId().toString()).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.comparison").value("MATCHED"));var snapshots=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("PAYMENT_RECONCILIATION",i.getDocument().getId().toString());assertEquals(2,transactions.count());assertEquals(2,snapshots.size());assertEquals(first,snapshots.getLast().getNewValue());}
 @Test void lookupAndDocumentOriginCreationUseRealScopedIds()throws Exception{var i=invoice(13,true);invoice(1,true);invoice(13,false);String auth=token("accountant");mvc.perform(get("/api/v1/financial-transactions/link-options/invoices").param("search",i.getInvoiceNumber()).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].invoiceId").value(i.getId().intValue()));mvc.perform(get("/api/v1/financial-transactions/link-options/documents").param("companyId","1").header("Authorization",auth)).andExpect(status().isForbidden());var request=body(null,"30.00");request.put("documentId",i.getDocument().getId());assertEquals(i.getId(),create(request,auth).get("invoiceId").asLong());}
 @Test void approvalHasNoPaymentSideEffect()throws Exception{var i=invoice(13,false);String auth=token("accountant");mvc.perform(post("/api/v1/documents/"+i.getDocument().getId()+"/workflow").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\"}")).andExpect(status().isOk());assertEquals(0,transactions.count());assertEquals("UNPAID",summary(i,auth).get("paymentStatus").asText());}
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 void concurrentOverpaymentAndConcurrentRetryAreSerialized()throws Exception{for(boolean sameKey:new boolean[]{false,true}){var i=new TransactionTemplate(manager).execute(s->invoice(13,true));var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);String shared=UUID.randomUUID().toString();try{
 Callable<Long> call=()->{barrier.await(10,TimeUnit.SECONDS);var request=new FinancialTransactionRequest("EXPENSE",new BigDecimal("80.00"),LocalDate.of(2026,10,16),null,null,null,i.getId(),"CASH",13L,sameKey?shared:UUID.randomUUID().toString(),true,null);try{return service.create(request,new TestingAuthenticationToken("accountant","unused","PERMISSION_ACCOUNTING_CREATE")).id();}catch(org.springframework.web.server.ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return null;}};
 var a=pool.submit(call);var b=pool.submit(call);Long x=a.get(20,TimeUnit.SECONDS),y=b.get(20,TimeUnit.SECONDS);if(sameKey)assertEquals(x,y);else assertTrue((x==null)!=(y==null));assertEquals(1,transactions.linked(13L,i.getDocument().getId(),i.getId()).size());
 }finally{pool.shutdownNow();new TransactionTemplate(manager).executeWithoutResult(s->{var rows=transactions.linked(13L,i.getDocument().getId(),i.getId());for(var t:rows)audit.deleteAll(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("FINANCIAL_TRANSACTION",t.getId().toString()));transactions.deleteAll(rows);invoices.deleteById(i.getId());documents.deleteById(i.getDocument().getId());});}}}

 @Test void fractionalAmountsAreExactAndCategoryRulesDoNotInferLegacyType()throws Exception {
   var i=invoice(13,true);var c=new AccountingCategory();c.setCompany(i.getDocument().getCompany());c.setCategoryCode("LEGACY");c.setCategoryName("Unknown legacy type");c=categories.saveAndFlush(c);String auth=token("accountant");
   for(String amount:List.of("33.33","33.33","33.34")){var request=body(i,amount);request.put("categoryId",c.getId());create(request,auth);}
   assertEquals("PAID",summary(i,auth).get("paymentStatus").asText());assertEquals(0,summary(i,auth).get("remaining").decimalValue().compareTo(BigDecimal.ZERO));assertNull(categories.findById(c.getId()).orElseThrow().getCategoryType());
   c.setActive(false);categories.flush();var invalid=body(null,"1.00");invalid.put("categoryId",c.getId());mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
   invalid.remove("categoryId");invalid.remove("paymentMethod");mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());assertEquals(3,transactions.count());
 }
 @Test void updateCannotOverpayAndEmployeeCannotEditOrCancel()throws Exception {
   var i=invoice(13,true);String auth=token("accountant");var first=create(body(i,"40.00"),auth);create(body(i,"60.00"),auth);var edit=body(i,"41.00");edit.put("expectedUpdatedAt",first.get("updatedAt").asText());String path="/api/v1/financial-transactions/"+first.get("id").asLong();
   mvc.perform(put(path).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_EXCEEDS_TOTAL"));String employee=token("employee");
   mvc.perform(put(path).header("Authorization",employee).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit))).andExpect(status().isForbidden());
   mvc.perform(post(path+"/cancel").header("Authorization",employee).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason","attempt","expectedUpdatedAt",first.get("updatedAt").asText())))).andExpect(status().isForbidden());assertEquals(100,summary(i,auth).get("confirmedPaid").asInt());
 }

 @Test void inactiveAdminAssignmentCannotBypassCashCompanyScope()throws Exception {
   var role=roles.findByCode("ADMIN").orElseThrow();role.setActive(false);roles.flush();
   var accountant=users.findByUsername("accountant").orElseThrow();var assignment=new UserRoleAssignment();assignment.setUser(accountant);assignment.setRole(role);accountant.getUserRoles().add(assignment);users.saveAndFlush(accountant);
   String auth=token("accountant");mvc.perform(get("/api/v1/dashboard/financial").param("companyId","1").header("Authorization",auth)).andExpect(status().isForbidden());
   mvc.perform(get("/api/v1/financial-transactions/link-options/invoices").param("companyId","1").header("Authorization",auth)).andExpect(status().isForbidden());
 }

 @Test void cashExportUsesSelectedCompanyAndDeniesCrossCompanyAccountant()throws Exception {
   String accountant=token("accountant"),admin=token("admin");create(body(null,"5.00"),accountant);var foreign=body(null,"7.00");foreign.put("companyId",1);create(foreign,admin);
   mvc.perform(get("/api/v1/exports/financial-report").param("companyId","1").header("Authorization",accountant)).andExpect(status().isForbidden());
   var bytes=mvc.perform(get("/api/v1/exports/financial-report").param("companyId","1").header("Authorization",admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
   try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))){assertEquals(7,book.getSheet("Tong quan").getRow(1).getCell(3).getNumericCellValue());}
 }

 @Test void timeFilterSeventeenDaysMatchesEligibleDatabaseTotalsAndExcel() throws Exception {
   String auth=token("accountant");
   var income=body(null,"100.25");income.put("transactionType","INCOME");income.put("transactionDate","2026-10-01");create(income,auth);
   var expense=body(null,"30.10");expense.put("transactionDate","2026-10-17");create(expense,auth);
   for(String date:List.of("2026-09-30","2026-10-18")){var outside=body(null,"999.00");outside.put("transactionDate",date);create(outside,auth);}
   var cancelled=create(body(null,"9.00"),auth);
   mvc.perform(post("/api/v1/financial-transactions/"+cancelled.get("id").asLong()+"/cancel").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason","Isolated test","expectedUpdatedAt",cancelled.get("updatedAt").asText())))).andExpect(status().isOk());
   var legacy=new FinancialTransaction();legacy.setCompany(companies.findById(13L).orElseThrow());legacy.setTransactionDate(LocalDate.of(2026,10,3));legacy.setAmount(new BigDecimal("1000"));legacy.setTransactionType("INCOME");legacy.setStatus("ACTIVE");transactions.saveAndFlush(legacy);
   var foreign=body(null,"800.00");foreign.put("companyId",1);create(foreign,token("admin"));
   var result=financialSeries(auth,"2026-10-01","2026-10-17","DAILY",null);
   assertEquals(17,result.size());assertEquals("2026-10-01",result.get(0).get("period").asText());assertEquals("2026-10-17",result.get(16).get("period").asText());
   BigDecimal totalIncome=BigDecimal.ZERO,totalExpense=BigDecimal.ZERO;
   for(var row:result){totalIncome=totalIncome.add(row.get("income").decimalValue());totalExpense=totalExpense.add(row.get("expense").decimalValue());}
   assertEquals(0,totalIncome.compareTo(new BigDecimal("100.25")));assertEquals(0,totalExpense.compareTo(new BigDecimal("30.10")));assertEquals(0,result.get(1).get("income").decimalValue().compareTo(BigDecimal.ZERO));
   var baseline=transactions.summarize(13L,LocalDate.of(2026,10,1),LocalDate.of(2026,10,17)).getFirst();
   assertEquals(0,totalIncome.compareTo((BigDecimal)baseline[0]));assertEquals(0,totalExpense.compareTo((BigDecimal)baseline[1]));
   var totals=json.readTree(mvc.perform(get("/api/v1/dashboard/financial").param("dateFrom","2026-10-01").param("dateTo","2026-10-17").header("Authorization",auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   assertEquals(0,totalIncome.compareTo(totals.get("totalRevenue").decimalValue()));assertEquals(0,totalExpense.compareTo(totals.get("totalExpense").decimalValue()));
   byte[] export=mvc.perform(get("/api/v1/exports/financial-report").param("fromDate","2026-10-01").param("toDate","2026-10-17").header("Authorization",auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
   try(var workbook=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(export))){
    var row=workbook.getSheet("Tong quan").getRow(1);assertEquals(100.25,row.getCell(2).getNumericCellValue(),0.00001);assertEquals(30.10,row.getCell(3).getNumericCellValue(),0.00001);
    assertEquals(LocalDate.of(2026,10,1),row.getCell(0).getLocalDateTimeCellValue().toLocalDate());assertEquals(LocalDate.of(2026,10,17),row.getCell(1).getLocalDateTimeCellValue().toLocalDate());
    assertEquals(2,workbook.getSheet("Thu chi").getLastRowNum());
   }
 }
 JsonNode financialSeries(String auth,String from,String to,String interval,Long company) throws Exception {
  var request=get("/api/v1/dashboard/financial/time-series").param("dateFrom",from).param("dateTo",to).param("interval",interval).header("Authorization",auth);
  if(company!=null)request.param("companyId",company.toString());
  return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 @Test void timeFilterCrossMonthYearLeapDayAndEmptyBuckets() throws Exception {
  String auth=token("accountant");
  for(String[] pair:List.of(new String[]{"2026-10-31","2026-11-01"},new String[]{"2026-12-31","2027-01-01"},new String[]{"2024-02-29","2024-03-01"})){
   for(String date:pair){var request=body(null,"1.10");request.put("transactionDate",date);create(request,auth);}
   var daily=financialSeries(auth,pair[0],pair[1],"DAILY",null);assertEquals(2,daily.size());assertEquals(pair[0],daily.get(0).get("period").asText());assertEquals(pair[1],daily.get(1).get("period").asText());
   var monthly=financialSeries(auth,pair[0],pair[1],"MONTHLY",null);assertEquals(2,monthly.size());assertEquals(0,monthly.get(0).get("expense").decimalValue().compareTo(new BigDecimal("1.10")));
  }
  assertEquals(29,financialSeries(auth,"2024-02-01","2024-02-29","DAILY",null).size());
  var empty=financialSeries(auth,"2028-01-01","2028-01-01","DAILY",null);assertEquals(1,empty.size());assertEquals(0,empty.get(0).get("income").asInt());
 }
 @Test void timeSeriesCompanyScopeRbacAndInvalidRanges() throws Exception {
  String auth=token("accountant");var request=body(null,"5.00");create(request,auth);
  mvc.perform(get("/api/v1/dashboard/financial/time-series").param("companyId","1").header("Authorization",auth)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/dashboard/financial/time-series").header("Authorization",token("employee"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/dashboard/financial/time-series").param("dateFrom","2026-10-17").param("dateTo","2026-10-01").header("Authorization",auth)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/dashboard/financial/time-series").param("interval","HOURLY").header("Authorization",auth)).andExpect(status().isBadRequest());
  var all=financialSeries(token("admin"),"2026-10-16","2026-10-16","DAILY",13L);assertEquals(5,all.get(0).get("expense").asInt());
  var other=financialSeries(token("admin"),"2026-10-16","2026-10-16","DAILY",1L);assertEquals(0,other.get(0).get("expense").asInt());
 }

 @Test void financialAggregatesAcceptAbsentOrOneSidedDateFilters() throws Exception {
  String auth=token("accountant");create(body(null,"7.35"),auth);
  for(var parameters:List.of(Map.<String,String>of(),Map.of("dateFrom","2026-10-16"),Map.of("dateTo","2026-10-16"))){
   for(String path:List.of("/api/v1/dashboard/financial","/api/v1/dashboard/financial/time-series")){
    var request=get(path).header("Authorization",auth);parameters.forEach((key,value)->request.param(key,value));
    var result=mvc.perform(request).andExpect(status().isOk()).andReturn();var node=json.readTree(result.getResponse().getContentAsString());
    var amount=node.isArray()?node.get(0).get("expense"):node.get("totalExpense");assertEquals(0,amount.decimalValue().compareTo(new BigDecimal("7.35")));
   }
  }
 }

 JsonNode preview(Invoice invoice,String auth,Long editingId)throws Exception{
  var request=get("/api/v1/financial-transactions/payment-preview").param("invoiceId",invoice.getId().toString()).header("Authorization",auth);if(editingId!=null)request.param("editingId",editingId.toString());
  return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 @Test void paymentPreviewShowsUnpaidPartialAndPaidWithoutCreatingPayment() throws Exception {
  var invoice=invoice(13,true);String auth=token("accountant");
  var unpaid=preview(invoice,auth,null);assertEquals(100,unpaid.get("availableAmount").asInt());assertTrue(unpaid.get("canCreate").asBoolean());assertEquals(0,transactions.count());
  var unconfirmed=body(invoice,"40.00");unconfirmed.put("paymentConfirmed",false);
  mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(unconfirmed))).andExpect(status().isBadRequest());assertEquals(0,transactions.count());
  var payment=body(invoice,"40.00");payment.put("transactionDate","2026-10-17");payment.put("description","Thanh toán một phần đã kiểm tra");var first=create(payment,auth);
  mvc.perform(get("/api/v1/financial-transactions").param("invoiceId",invoice.getId().toString()).param("dateFrom","2026-10-17").param("dateTo","2026-10-17").header("Authorization",auth))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].transactionDate").value("2026-10-17"))
   .andExpect(jsonPath("$.content[0].description").value("Thanh toán một phần đã kiểm tra")).andExpect(jsonPath("$.content[0].documentId").value(invoice.getDocument().getId().intValue()))
   .andExpect(jsonPath("$.content[0].paymentConfirmation").value("CONFIRMED"));assertEquals(LocalDate.of(2026,10,16),invoice.getInvoiceDate());
  var partial=preview(invoice,auth,null);assertEquals(40,partial.get("summary").get("confirmedPaid").asInt());assertEquals(60,partial.get("availableAmount").asInt());
  assertEquals(100,preview(invoice,auth,first.get("id").asLong()).get("availableAmount").asInt());
  create(body(invoice,"60.00"),auth);var paid=preview(invoice,auth,null);assertFalse(paid.get("canCreate").asBoolean());assertEquals("PAYMENT_FULLY_PAID",paid.get("blockedCode").asText());assertEquals(2,transactions.count());
 }
 @Test void paymentPreviewUsesInvoiceBusinessDirectionAndRejectsInvalidDirectionOnSave() throws Exception {
  var invoice=invoice(13,true);var company=invoice.getDocument().getCompany();company.setTaxCode("0312345678");companies.flush();invoice.setSellerTaxCode("0312345678");invoice.setBuyerTaxCode("0399999999");invoices.flush();
  String auth=token("accountant");var result=preview(invoice,auth,null);assertEquals("INCOME",result.get("link").get("suggestedTransactionType").asText());assertEquals("INVOICE_COMPANY_TAX_CODE",result.get("directionSource").asText());
  mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(invoice,"1.00")))).andExpect(status().isBadRequest());
  invoice.setSellerTaxCode("0399999999");invoice.setBuyerTaxCode("0312345678");invoices.flush();assertEquals("EXPENSE",preview(invoice,auth,null).get("link").get("suggestedTransactionType").asText());assertEquals(0,transactions.count());
 }
 @Test void paymentPreviewEnforcesPermissionsCompanyAndInvalidTotals() throws Exception {
  var foreign=invoice(1,true);var invalid=invoice(13,true);invalid.setTotalAmount(null);invoices.flush();String auth=token("accountant");
  mvc.perform(get("/api/v1/financial-transactions/payment-preview").param("invoiceId",foreign.getId().toString()).header("Authorization",auth)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/financial-transactions/payment-preview").param("invoiceId",invalid.getId().toString()).header("Authorization",token("employee"))).andExpect(status().isForbidden());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->paymentLinks.preview(null,invalid.getId(),13L,null,new TestingAuthenticationToken("accountant","unused","PERMISSION_ACCOUNTING_VIEW")));
  var result=preview(invalid,auth,null);assertFalse(result.get("canCreate").asBoolean());assertEquals("MISSING_PAYMENT_TOTAL",result.get("blockedCode").asText());assertEquals(0,transactions.count());
 }
 @Test void legacyUniqueIndexLimitsBlockAdditionalPaymentButNotIdempotentRetryOrEdit() throws Exception {
  var invoice=invoice(13,true);String auth=token("accountant");var request=body(invoice,"40.00");var first=create(request,auth);
  org.mockito.Mockito.doReturn(new PaymentSchemaGuard.Limits(true,true)).when(paymentSchema).inspect();
  var result=preview(invoice,auth,null);assertFalse(result.get("canCreate").asBoolean());assertEquals("PAYMENT_SCHEMA_REVIEW_REQUIRED",result.get("blockedCode").asText());
  mvc.perform(post("/api/v1/financial-transactions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(invoice,"60.00")))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_SCHEMA_REVIEW_REQUIRED"));
  assertEquals(first.get("id"),create(request,auth).get("id"));assertTrue(preview(invoice,auth,first.get("id").asLong()).get("canCreate").asBoolean());assertEquals(1,transactions.count());
 }

 @Test void invoicePickerOptionsUseInvoiceIdsAndCreateBothDirectionsWithoutDuplicateLinks()throws Exception{
  String auth=token("accountant");
  for(String type:List.of("INCOME","EXPENSE")){
   var invoice=invoice(13,true);var id=invoice.getId();
   var response=json.readTree(mvc.perform(get("/api/v1/financial-transactions/link-options/invoices").param("search",invoice.getInvoiceNumber()).header("Authorization",auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   assertEquals(1,response.get("totalElements").asInt());var option=response.get("content").get(0);assertEquals(id.longValue(),option.get("invoiceId").asLong());assertEquals(invoice.getDocument().getId().longValue(),option.get("documentId").asLong());
   var preview=preview(invoice,auth,null);assertEquals(id.longValue(),preview.get("link").get("invoiceId").asLong());
   var request=body(invoice,"20.00");request.put("transactionType",type);assertFalse(request.containsKey("documentId"));var saved=create(request,auth);
   assertEquals(id.longValue(),saved.get("invoiceId").asLong());assertEquals(invoice.getDocument().getId().longValue(),saved.get("documentId").asLong());assertEquals(type,saved.get("transactionType").asText());
   assertEquals(1,transactions.linked(13L,invoice.getDocument().getId(),id).size());
  }
  var manual=body(null,"10.00");var saved=create(manual,auth);assertTrue(saved.get("documentId").isNull());assertTrue(saved.get("invoiceId").isNull());assertEquals(3,transactions.count());
 }

 @Autowired ClassificationRepository classificationRepository;
 @Test void previewAndSearchDoNotAutofillUntypedOrWrongDirectionCategory()throws Exception{
  var invoice=invoice(13,true);invoice.getDocument().getCompany().setTaxCode("0312345678");
  invoice.setSellerTaxCode("0399999999");invoice.setBuyerTaxCode("0312345678");companies.flush();invoices.flush();
  var category=new AccountingCategory();category.setCompany(invoice.getDocument().getCompany());category.setCategoryCode("TEST_DIRECTION");category.setCategoryName("Mua hàng");category.setActive(true);category=categories.saveAndFlush(category);
  var classification=new Classification();classification.setDocument(invoice.getDocument());classification.setAccountingCategory(category);classificationRepository.saveAndFlush(classification);
  String auth=token("accountant");assertTrue(preview(invoice,auth,null).get("link").get("suggestedCategoryId").isNull());
  category.setCategoryType(AccountingCategoryType.INCOME);categories.flush();assertTrue(preview(invoice,auth,null).get("link").get("suggestedCategoryId").isNull());
  category.setCategoryType(AccountingCategoryType.EXPENSE);categories.flush();assertEquals(category.getId().longValue(),preview(invoice,auth,null).get("link").get("suggestedCategoryId").asLong());
  invoice.setBuyerTaxCode(null);invoices.flush();var unknown=preview(invoice,auth,null);assertTrue(unknown.get("link").get("suggestedTransactionType").isNull());assertTrue(unknown.get("link").get("suggestedCategoryId").isNull());assertEquals("UNKNOWN",unknown.get("directionSource").asText());
  var response=mvc.perform(get("/api/v1/financial-transactions/link-options/invoices").param("documentId",invoice.getDocument().getId().toString()).header("Authorization",auth)).andExpect(status().isOk()).andReturn();
  var option=json.readTree(response.getResponse().getContentAsString()).get("content").get(0);assertTrue(option.get("suggestedTransactionType").isNull());assertTrue(option.get("suggestedCategoryId").isNull());
 }
}
