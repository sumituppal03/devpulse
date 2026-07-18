package com.devpulse.prcontext;

import com.devpulse.shared.webhook.GitHubWebhookSignatureVerifier;
import com.devpulse.shared.webhook.WebhookEvent;
import com.devpulse.shared.webhook.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookController {

    private final GitHubWebhookSignatureVerifier signatureVerifier;
    private final WebhookEventRepository webhookEventRepository;
    private final PrContextEnricherService prContextEnricherService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhooks/github")
    public ResponseEntity<Void> receiveGitHubWebhook(
            HttpServletRequest request,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType) throws IOException {

        // Raw bytes — must be read before any parsing to preserve
        // the exact bytes GitHub signed for HMAC verification
        String rawPayload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!signatureVerifier.isValid(rawPayload, signature)) {
            log.warn("Rejected webhook with invalid signature for event type: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WebhookEvent event = webhookEventRepository.save(
                WebhookEvent.create("GITHUB", eventType, rawPayload));
        log.info("Logged GitHub webhook event: {} (id={})", eventType, event.getId());

        if ("pull_request".equals(eventType)) {
            try {
                GitHubPullRequestWebhookPayload payload =
                        objectMapper.readValue(rawPayload, GitHubPullRequestWebhookPayload.class);

                if ("opened".equals(payload.action()) || "reopened".equals(payload.action())) {
                    // Extract branch name for Linear ticket lookup
                    String branchName = payload.pullRequest().head() != null
                            ? payload.pullRequest().head().ref()
                            : null;

                    prContextEnricherService.enrich(
                            event.getId(),
                            payload.repository().owner().login(),
                            payload.repository().name(),
                            payload.pullRequest().number(),
                            payload.pullRequest().title(),
                            payload.pullRequest().body(),
                            branchName  // new — passed to LinearClient for ticket lookup
                    );
                }
            } catch (Exception e) {
                log.error("Failed to parse pull_request webhook payload", e);
            }
        }

        return ResponseEntity.ok().build();
    }
}
