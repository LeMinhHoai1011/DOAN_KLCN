package com.example.invoice.service;

import com.example.invoice.ai.*;
import com.example.invoice.config.*;
import com.example.invoice.controller.BenchmarkController;
import com.example.invoice.dto.ai.*;
import com.example.invoice.security.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real embedded HTTP + real JWT filter. No datasource/JPA/MinIO beans; processor output is a contract fixture. */
@SpringBootTest(classes=BenchmarkHttpTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.config.location=optional:classpath:/benchmark-test-empty.yml","benchmark.enabled=true",
 "app.jwt.secret=benchmark-test-only-secret-at-least-32-bytes","app.jwt.expiration-ms=600000","logging.level.root=WARN","debug=false"})
@org.springframework.test.context.ActiveProfiles("benchmark-contract-test")
class BenchmarkHttpTest {
    @Configuration
    @Profile("benchmark-contract-test")
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,JpaRepositoriesAutoConfiguration.class})
    @Import({SecurityConfig.class,JwtAuthenticationFilter.class,JwtService.class,BenchmarkController.class})
    static class App {
        @Bean AiProperties properties(){var p=new AiProperties();p.getOllama().setModel("qwen3-vl:8b");return p;}
        @Bean okhttp3.OkHttpClient http(){return new okhttp3.OkHttpClient.Builder().build();}
        @Bean CustomUserDetailsService users(){
            var service=mock(CustomUserDetailsService.class);
            when(service.loadUserByUsername(anyString())).thenAnswer(i->principal(i.getArgument(0)));
            return service;
        }
        @Bean OllamaAiProvider ollama(){return mock(OllamaAiProvider.class);}
        @Bean BenchmarkAdapter adapter(){return mock(BenchmarkAdapter.class);}
    }
    static VersionedUserDetails principal(String name) {
        return new VersionedUserDetails(name,"unused",true,List.of(new SimpleGrantedAuthority(name.equals("admin")?"ROLE_ADMIN":"ROLE_EMPLOYEE")),0);
    }
    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired BenchmarkAdapter adapter;
    @Autowired OllamaAiProvider ollama;
    @Autowired org.springframework.context.ApplicationContext context;
    @TempDir Path temp;
    String token(String user){return jwt.generateToken(principal(user));}
    HttpResponse<String> get(String path,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void realJwtRequiresAdminAndNoDatabaseExists() throws Exception {
        assertEquals(401,get("/api/benchmark/health",null).statusCode());
        // The existing security chain may re-dispatch sendError(403) to authenticated /error as 401.
        assertTrue(Set.of(401,403).contains(get("/api/benchmark/health",token("employee")).statusCode()));
        assertEquals(200,get("/api/benchmark/health",token("admin")).statusCode());
        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(io.minio.MinioClient.class).isEmpty());
    }
    @Test void pythonRunnerCallsJavaRestAndExportsExcel() throws Exception {
        var nativeOcr=new OcrDocumentResult("TEST INVOICE",Float.NaN,List.of(new OcrPageResult(1,0,0,"TEST INVOICE",Float.NaN,List.of(),0,null,"pdf-text-layer")),0,List.of());
        var parsed=AiDocumentResultParser.parse("ollama","qwen3-vl:8b",BenchmarkAdapterTest.JSON,BenchmarkAdapterTest.JSON,1);
        var normalized=new AiDocumentResultValidator().validate(parsed,Set.of("RETAIL_INVOICE"));
        when(adapter.run(any(),anyString(),anyBoolean(),any())).thenAnswer(i->new BenchmarkAdapter.Output("SUCCESS",null,nativeOcr,
            (boolean)i.getArgument(2)?parsed:null,(boolean)i.getArgument(2)?normalized:null,List.of(),0,1));
        // aiReady uses the real connectivity contract; local stub only, no external model.
        var tags=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        tags.createContext("/api/tags",e->{byte[] b="{\"models\":[{\"name\":\"qwen3-vl:8b\",\"digest\":\"test-only\"}]}".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});tags.start();
        context.getBean(AiProperties.class).getOllama().setBaseUrl("http://127.0.0.1:"+tags.getAddress().getPort());
        when(ollama.checkConnectivity()).thenReturn(new AiConnectivityResponse("ollama","qwen3-vl:8b",List.of("qwen3-vl:8b")));
        try {
            Path project=Path.of("../..").toAbsolutePath().normalize();
            Path data=Files.createDirectory(temp.resolve("dataset"));
            Files.write(data.resolve("sample.png"),new byte[]{(byte)137,80,78,71,13,10,26,10});
            Path config=temp.resolve("config.yaml");
            Files.writeString(config,"api_url: http://127.0.0.1:"+port+"\n"+
                "dataset: '"+data+"'\nresults: '"+temp.resolve("results")+"'\nground_truth: '"+temp.resolve("gt")+"'\nreports: '"+temp.resolve("reports")+"'\n"+
                "options:\n  documentTypes: [RETAIL_INVOICE]\n  categories: []\n");
            Path python=project.resolve("benchmark_runner/.venv/Scripts/python.exe");
            assertTrue(Files.isRegularFile(python),"Install benchmark_runner requirements into its venv first");
            for(var arguments:List.of(List.of("run","--run-id","http-ocr","--mode","ocr"),List.of("run","--run-id","http-ai","--mode","ai"),List.of("report","--run-id","http-ai"))) {
                var command=new ArrayList<String>(List.of(python.toString(),project.resolve("benchmark_runner/main.py").toString(),"--config",config.toString()));command.addAll(arguments);
                var pb=new ProcessBuilder(command).redirectErrorStream(true);pb.environment().put("BENCHMARK_JWT",token("admin"));
                var process=pb.start();assertTrue(process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes()));
            }
            try(var files=Files.list(temp.resolve("reports"))){assertTrue(files.anyMatch(p->p.toString().endsWith(".xlsx")));}
        }finally{tags.stop(0);}
    }
}
