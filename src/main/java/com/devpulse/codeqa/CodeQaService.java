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

    /**
     * Answers a natural-language question about a specific repository.
     *
     * repositoryId is now properly passed to the similarity search — previously
     * it was validated but then ignored, meaning a question about repo A could
     * return chunks from repo B if the tenant had multiple repos registered.
     * This is fixed: results always come from the exact repo the user asked about.
     */
    public CodeQaResponse ask(UUID tenantId, UUID repositoryId, String question) {

        // 1. Embed the question using the same model as the indexed chunks
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        String queryVector = toVectorString(questionEmbedding.vector());

        // 2. Similarity search — scoped to tenant AND this specific repository
        List<CodeChunk> relevantChunks = codeChunkRepository.findTopKSimilar(
                tenantId.toString(),
                repositoryId.toString(),
                queryVector,
                TOP_K
        );

        // 3. No chunks found — repo may not be indexed yet
        if (relevantChunks.isEmpty()) {
            return new CodeQaResponse(
                    "No indexed content found for this repository. " +
                    "Trigger indexing at POST /api/v1/repos/{id}/index and wait for status READY.",
                    List.of(), 0, false
            );
        }

        // 4. Build grounded context with file citations
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

        // 5. Grounded LLM call — same system/user split as standup and PR context
        SystemMessage systemMessage = SystemMessage.from("""
                You are a technical assistant for an engineering team.
                Answer questions about their codebase based ONLY on the code context provided.
                Always cite the specific file path where you found the answer.
                If the context does not contain enough information to answer confidently,
                say: "I don't have enough context in the indexed codebase to answer this."
                Never invent code or explanations not present in the provided context.
                Be concise and technical.
                """);

        UserMessage userMessage = UserMessage.from("""
                Question: %s

                Relevant code context (cite these files in your answer):
                %s
                """.formatted(question, context));

        ChatResponse chatResponse = chatModel.chat(systemMessage, userMessage);
        String answer = chatResponse.aiMessage().text();

        log.info("CodeQA answered for tenant={} repo={} — {} chunks from {} files",
                tenantId, repositoryId, relevantChunks.size(), sources.size());

        return new CodeQaResponse(answer, sources, relevantChunks.size(), true);
    }

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
