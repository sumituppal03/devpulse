package com.devpulse;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DevpulseApplicationTests {

    // Our Flyway migrations use real Postgres features (pgcrypto, TIMESTAMPTZ,
    // and the pgvector extension for embeddings). H2 cannot emulate pgvector,
    // so tests run against an actual throwaway Postgres instance via Testcontainers.
    // @ServiceConnection auto-wires spring.datasource.* to point at this container.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    // Prevents Spring from trying to connect to Ollama during test context load.
    // The real ChatModel and EmbeddingModel beans require a live Ollama instance
    // which is not available in CI or during unit test runs.
    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
        // Verifies the entire Spring context wires together correctly.
        // If any bean is misconfigured, this test fails immediately
        // rather than discovering it at runtime.
    }
}