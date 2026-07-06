package com.devpulse;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;

@SpringBootTest
@ActiveProfiles("test")
class DevpulseApplicationTests {

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