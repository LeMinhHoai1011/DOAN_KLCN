package com.example.invoice.controller;
import com.example.invoice.exception.GlobalExceptionHandler;
import com.example.invoice.service.DashboardService;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
class FinancialDatabaseErrorResponseTest {
 @Test void aggregateDatabaseFailureIsHttp500AndDoesNotExposeSqlStateOrSql() throws Exception {
  var service=mock(DashboardService.class);
  var failure=new InvalidDataAccessResourceUsageException("protected SQL details",new SQLException("could not determine data type of parameter $3 private-token","42P18"));
  when(service.financialScoped(any(),any(),any())).thenThrow(failure);
  when(service.timeSeriesScoped(any(),any(),any(),any())).thenThrow(failure);
  var mvc=MockMvcBuilders.standaloneSetup(new DashboardController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
  for(String path:new String[]{"/api/v1/dashboard/financial","/api/v1/dashboard/financial/time-series"}){
   mvc.perform(get(path).param("dateFrom","2026-10-01").param("dateTo","2026-10-17"))
    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("DATABASE_ERROR"))
    .andExpect(jsonPath("$.message").value("Thao tác với cơ sở dữ liệu thất bại"))
    .andExpect(content().string(not(containsString("42P18")))).andExpect(content().string(not(containsString("private-token"))));
  }
 }
}
