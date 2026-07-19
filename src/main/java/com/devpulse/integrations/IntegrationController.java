package com.devpulse.integrations;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    /**
     * Configures the Slack integration for the authenticated tenant.
     *
     * How to get a Slack webhook URL:
     * 1. Go to https://api.slack.com/apps
     * 2. Create a new app -> "From scratch"
     * 3. Enable Incoming Webhooks
     * 4. Add New Webhook to Workspace -> pick a channel
     * 5. Copy the webhook URL and paste it here
     *
     * DevPulse will immediately send a test message to verify the URL works.
     */
    @PostMapping("/slack")
    public ResponseEntity<Map<String, Object>> configureSlack(
            @RequestBody Map<String, @NotBlank String> body) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        String webhookUrl = body.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "webhookUrl is required"
            ));
        }

        boolean success = integrationService.configureSlack(tenantId, webhookUrl);

        if (!success) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to connect to Slack. Please check your webhook URL and try again.",
                    "hint", "The webhook URL should look like https://hooks.slack.com/services/T.../B.../..."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Slack connected successfully! A test message was posted to your channel.",
                "status", "CONNECTED"
        ));
    }

    /**
     * Configures the Linear integration for the authenticated tenant.
     *
     * How to get a Linear API key:
     * 1. Go to Linear -> Settings -> API
     * 2. Create a personal API key
     * 3. Paste it here
     *
     * Once configured, PR comments will automatically include the
     * Linear ticket title and description when a branch name contains
     * a ticket ID like LIN-234.
     */
    @PostMapping("/linear")
    public ResponseEntity<Map<String, String>> configureLinear(
            @RequestBody Map<String, @NotBlank String> body) {

        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        String apiKey = body.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "apiKey is required"
            ));
        }

        integrationService.configureLinear(tenantId, apiKey);

        return ResponseEntity.ok(Map.of(
                "message", "Linear connected. PR comments will now include ticket context when branch names contain Linear ticket IDs (e.g. feature/LIN-234-my-feature).",
                "status", "CONNECTED"
        ));
    }

    /**
     * Returns the current integration status for the tenant.
     * Never returns the actual credentials -- only whether each is configured.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        return ResponseEntity.ok(Map.of(
                "slack", Map.of(
                        "configured", integrationService.isSlackConfigured(tenantId),
                        "description", "Post standups to a Slack channel automatically"
                ),
                "linear", Map.of(
                        "configured", integrationService.isLinearConfigured(tenantId),
                        "description", "Add Linear ticket context to PR comments"
                )
        ));
    }

    /**
     * Disables an integration without deleting the configuration.
     * Calling POST /integrations/slack again re-enables it.
     */
    @DeleteMapping("/{type}")
    public ResponseEntity<Map<String, String>> disable(@PathVariable String type) {
        UUID tenantId = (UUID) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        String integType = type.toUpperCase();
        if (!integType.equals("SLACK") && !integType.equals("LINEAR")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unknown integration type. Use 'slack' or 'linear'."
            ));
        }

        integrationService.disableIntegration(tenantId, integType);
        return ResponseEntity.ok(Map.of(
                "message", type + " integration disabled."
        ));
    }
}
