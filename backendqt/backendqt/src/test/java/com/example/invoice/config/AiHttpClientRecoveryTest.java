package com.example.invoice.config;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

/**
 * Regression: a server listening on 127.0.0.1 only (like Ollama), addressed as "localhost".
 * After one call times out, the next call must still reach it instead of failing on ::1.
 */
class AiHttpClientRecoveryTest {
    @Test void callAfterATimeoutStillReachesAnIpv4OnlyServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(3_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.createContext("/fast", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            OkHttpClient shared = new AiConfiguration().aiHttpClient();
            // Same per-call settings as OllamaAiProvider: short timeout, no connection retries.
            OkHttpClient timed = shared.newBuilder().retryOnConnectionFailure(false).callTimeout(Duration.ofMillis(500)).build();
            RequestBody json = RequestBody.create("{}", MediaType.get("application/json"));
            assertThrows(InterruptedIOException.class, () -> timed.newCall(new Request.Builder().url(base + "/slow").post(json).build()).execute().close());
            OkHttpClient normal = shared.newBuilder().retryOnConnectionFailure(false).callTimeout(Duration.ofSeconds(5)).build();
            for (int i = 0; i < 3; i++) try (Response response = normal.newCall(new Request.Builder().url(base + "/fast").post(json).build()).execute()) {
                assertEquals(200, response.code());
            }
        } finally { server.stop(0); }
    }
}
