package com.devpulse.shared.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Configuration
public class ChatModelConfig {

    @Bean
    @Profile("!prod")
    public ChatModel ollamaChatModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model-name}") String modelName) {

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    @Profile("prod")
    public ChatModel groqChatModel(
            @Value("${groq.api-key}") String apiKey,
            @Value("${groq.model-name}") String modelName) {

        return OpenAiChatModel.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    // NEW — tracks whichever model name actually corresponds to the active ChatModel bean
    @Bean
    @Profile("!prod")
    public ActiveModelInfo activeModelInfoDev(@Value("${ollama.model-name}") String modelName) {
        return new ActiveModelInfo(modelName);
    }

    @Bean
    @Profile("prod")
    public ActiveModelInfo activeModelInfoProd(@Value("${groq.model-name}") String modelName) {
        return new ActiveModelInfo(modelName);
    }
}