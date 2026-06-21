package com.devpulse.shared.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Lives in `shared` for the same reason GitHubClient does: the standup
 * generator isn't the only feature that will ever need an LLM connection —
 * PR context enrichment and codebase Q&A will too. One connection, reused
 * everywhere, fixed in one place if it ever needs to change.
 */
@Configuration
public class OllamaConfig {

    @Bean
    public ChatModel chatModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model-name}") String modelName) {

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}