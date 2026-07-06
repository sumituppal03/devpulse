package com.devpulse.codeqa;

import com.devpulse.codeqa.dto.CodeQaResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeQaService {

    private static final int TOP_K = 5;

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final CodeChunkRepository codeChunkRepository;

    public CodeQaResponse ask(UUID tenantId, String question) {

        // 1. Embed the question using the same model as the indexed chunks
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        String queryVector = toVectorString(questionEmbedding.vector());

        // 2. Similarity search — tenant-scoped, so no cross-tenant data ever leaks
        List<CodeChunk> relevantChunks = codeChunkRepository.findTopKSimilar(
                tenantId.toString(),
                queryVector,
                TOP_K
        );

        // 3. If no chunks found, return immediately — LLM never called on empty context
        if (relevantChunks.isEmpty()) {
            return new CodeQaResponse(
                    "No indexed codebase found for this tenant. " +
                    "Please register a repository with POST /api/v1/repos and " +
                    "trigger indexing with POST /api/v1/repos/{id}/index first.",
                    List.of(), 0, false
            );
        }

        // 4. Build grounded context from retrieved chunks, with file citations
        String context = relevantChunks.stream()
                .map(chunk -> {
                    String location = chunk.getFilePath();
                    if (chunk.getClassName() != null)
                        location += " → " + chunk.getClassName();
                    if (chunk.getMethodName() != null)
                        location += "." + chunk.getMethodName() + "()";
                    return "[%s]\n%s".formatted(location, chunk.getContent());
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        List<String> sources = relevantChunks.stream()
                .map(CodeChunk::getFilePath)
                .distinct()
                .collect(Collectors.toList());

        // 5. Grounded LLM call — same system/user split pattern as standup feature
        SystemMessage systemMessage = SystemMessage.from("""
                You are a technical assistant for an engineering team.
                Answer questions about their codebase based ONLY on the code context provided below.
                Always cite the specific file path where you found the answer.
                If the context does not contain enough information to answer confidently,
                say exactly: "I don't have enough context in the indexed codebase to answer this."
                Never invent code or explanations that are not in the provided context.
                Be concise and technical.
                """);

        UserMessage userMessage = UserMessage.from("""
                Question: %s

                Relevant code context (cite these files in your answer):
                %s
                """.formatted(question, context));

        // ChatResponse.aiMessage().text() is the correct LangChain4j 1.x API
        // (not .content() which doesn't exist on ChatResponse in this version)
        ChatResponse chatResponse = chatModel.chat(systemMessage, userMessage);
        String answer = chatResponse.aiMessage().text();

        log.info("CodeQA answered for tenant {} — {} chunks from {} files",
                tenantId, relevantChunks.size(), sources.size());

        return new CodeQaResponse(answer, sources, relevantChunks.size(), true);
    }

    /**
     * Converts float[] to pgvector string "[0.12345,-0.67891,...]"
     * matching the format stored in code_chunks.embedding.
     */
    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
