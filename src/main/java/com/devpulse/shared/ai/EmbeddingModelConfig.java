package com.devpulse.shared.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * nomic-embed-text produces 768-dimensional embeddings — matches the
 * vector(768) column in code_chunks. Must be consistent: if you change
 * the model, every existing embedding in the database becomes invalid
 * and the entire index must be rebuilt from scratch.
 *
 * Run locally: ollama pull nomic-embed-text
 */
@Configuration
public class EmbeddingModelConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${ollama.base-url}") String baseUrl) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName("nomic-embed-text")
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}