package com.example.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:evaluation_api_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","app.evaluation.directory="})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(com.example.invoice.support.IsolatedAccountFixtures.class)
class EvaluationApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    String token(String name)throws Exception {
        String password=name.equals("admin")?"Admin@123456":name.equals("employee")?"Employee@123":"Accountant@123";
        var result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn();
        return "Bearer "+json.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
    @Test void anonymousCannotReadOrExport()throws Exception {
        for(String endpoint:new String[]{"statistics","export"})mvc.perform(get("/api/v1/evaluation/"+endpoint)).andExpect(status().isUnauthorized());
    }
    @Test void employeeAndAccountantCannotReadEvaluation()throws Exception {
        for(String name:new String[]{"employee","accountant"})for(String endpoint:new String[]{"statistics","export"})
            mvc.perform(get("/api/v1/evaluation/"+endpoint).header("Authorization",token(name))).andExpect(status().isForbidden());
    }
    @Test void adminSeesRealEmptyStateAndExportWithoutBusinessWrites()throws Exception {
        assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->c.getMetaData().getURL().startsWith("jdbc:h2:mem:evaluation_api_test")));
        Long before=jdbc.queryForObject("select count(*) from processing_logs",Long.class);
        String auth=token("admin");mvc.perform(get("/api/v1/evaluation/statistics").header("Authorization",auth)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.status").value("NOT_CONFIGURED")).andExpect(jsonPath("$.runs").isEmpty()).andExpect(jsonPath("$.comparable").value(false));
        mvc.perform(get("/api/v1/evaluation/export").header("Authorization",auth)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=ocr-ai-evaluation.json")).andExpect(jsonPath("$.runs").isEmpty());
        assertEquals(before,jdbc.queryForObject("select count(*) from processing_logs",Long.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from documents",Integer.class));
    }
    @Test void badFiltersAre400NotInternalError()throws Exception {
        String auth=token("admin");mvc.perform(get("/api/v1/evaluation/statistics").header("Authorization",auth).param("from","not-a-date")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_FILTER"));
        mvc.perform(get("/api/v1/evaluation/statistics").header("Authorization",auth).param("strategy","UNKNOWN")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_STRATEGY"));
    }
}
