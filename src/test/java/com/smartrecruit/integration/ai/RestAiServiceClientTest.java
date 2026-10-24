package com.smartrecruit.integration.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestAiServiceClientTest {

    @Test
    void healthSendsInternalKeyAndReturnsSafePayload() throws Exception {
        AtomicReference<String> receivedKey = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-AI-Service-Key"));
            respond(exchange, 200, """
                    {"status":"UP","service":"smartrecruit-ai-service","version":"1.0.0",
                     "embeddingModel":"fake-model","chromaAvailable":true,
                     "timestamp":"2026-10-24T10:00:00Z"}
                    """);
        });

        try {
            RestAiServiceClient client = new RestAiServiceClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-internal-key",
                    5, 120);

            var response = client.health();

            assertThat(response.status()).isEqualTo("UP");
            assertThat(response.chromaAvailable()).isTrue();
            assertThat(receivedKey.get()).isEqualTo("test-internal-key");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readTimeoutIsConvertedToSafeAiServiceException() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{}");
        });

        try {
            RestAiServiceClient client = new RestAiServiceClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-internal-key",
                    1, 120);

            assertThatThrownBy(client::health)
                    .isInstanceOf(AiServiceException.class)
                    .hasMessageContaining("indisponible");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void errorResponseDoesNotLeakServiceBody() throws Exception {
        HttpServer server = startServer(exchange ->
                respond(exchange, 500, "{\"detail\":\"secret internal trace\"}"));

        try {
            RestAiServiceClient client = new RestAiServiceClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-internal-key",
                    5, 120);

            assertThatThrownBy(client::health)
                    .isInstanceOf(AiServiceException.class)
                    .hasMessageNotContaining("secret internal trace");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", handler);
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
