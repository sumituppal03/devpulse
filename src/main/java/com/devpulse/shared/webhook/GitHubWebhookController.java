package com.devpulse.shared.webhook;

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

    @PostMapping("/webhooks/github")
    public ResponseEntity<Void> receiveGitHubWebhook(
            HttpServletRequest request,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event") String eventType) throws IOException {

        // Reading the raw stream ourselves — binding @RequestBody directly to
        // a String can misbehave when Content-Type is application/json, since
        // Jackson may try to parse it as a quoted JSON string rather than raw
        // bytes. This guarantees we get the EXACT bytes GitHub actually signed.
        String rawPayload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!signatureVerifier.isValid(rawPayload, signature)) {
            log.warn("Rejected webhook with invalid signature for event type: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WebhookEvent event = WebhookEvent.create("GITHUB", eventType, rawPayload);
        webhookEventRepository.save(event);
        log.info("Logged GitHub webhook event: {} (id={})", eventType, event.getId());

        return ResponseEntity.ok().build();
    }
}