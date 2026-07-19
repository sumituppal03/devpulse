package com.devpulse.integrations;

import com.devpulse.shared.slack.SlackClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationService {

    private final TenantIntegrationRepository integrationRepository;
    private final SlackClient slackClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean configureSlack(UUID tenantId, String webhookUrl) {
        boolean testPassed = slackClient.postMessage(webhookUrl,
                "DevPulse connected! Your standups will be posted here.");
        if (!testPassed) return false;
        String config = toJson(Map.of("webhookUrl", webhookUrl));
        saveOrUpdate(tenantId, "SLACK", config);
        log.info("Slack integration configured for tenant {}", tenantId);
        return true;
    }

    public boolean postStandupToSlack(UUID tenantId, String developerUsername,
                                       String date, String summary) {
        Optional<String> webhookUrl = getSlackWebhookUrl(tenantId);
        if (webhookUrl.isEmpty()) {
            log.debug("No Slack configured for tenant {} - skipping", tenantId);
            return false;
        }
        String message = slackClient.formatStandupMessage(developerUsername, date, summary);
        return slackClient.postMessage(webhookUrl.get(), message);
    }

    public Optional<String> getSlackWebhookUrl(UUID tenantId) {
        return integrationRepository
                .findByTenantIdAndIntegrationType(tenantId, "SLACK")
                .filter(TenantIntegration::isEnabled)
                .map(i -> extractField(i.getConfig(), "webhookUrl"));
    }

    public boolean isSlackConfigured(UUID tenantId) {
        return integrationRepository.existsByTenantIdAndIntegrationType(tenantId, "SLACK");
    }

    @Transactional
    public void configureLinear(UUID tenantId, String apiKey) {
        String config = toJson(Map.of("apiKey", apiKey));
        saveOrUpdate(tenantId, "LINEAR", config);
        log.info("Linear integration configured for tenant {}", tenantId);
    }

    public Optional<String> getLinearApiKey(UUID tenantId) {
        return integrationRepository
                .findByTenantIdAndIntegrationType(tenantId, "LINEAR")
                .filter(TenantIntegration::isEnabled)
                .map(i -> extractField(i.getConfig(), "apiKey"));
    }

    public boolean isLinearConfigured(UUID tenantId) {
        return integrationRepository.existsByTenantIdAndIntegrationType(tenantId, "LINEAR");
    }

    public List<TenantIntegration> getAllLinearIntegrations() {
        return integrationRepository.findAll().stream()
                .filter(i -> "LINEAR".equals(i.getIntegrationType()))
                .filter(TenantIntegration::isEnabled)
                .toList();
    }

    public String extractLinearApiKey(TenantIntegration integration) {
        return extractField(integration.getConfig(), "apiKey");
    }

    @Transactional
    public void disableIntegration(UUID tenantId, String integrationType) {
        integrationRepository
                .findByTenantIdAndIntegrationType(tenantId, integrationType)
                .ifPresent(i -> {
                    i.setEnabled(false);
                    integrationRepository.save(i);
                });
    }

    private void saveOrUpdate(UUID tenantId, String type, String config) {
        TenantIntegration integration = integrationRepository
                .findByTenantIdAndIntegrationType(tenantId, type)
                .orElseGet(() -> TenantIntegration.create(tenantId, type, config));
        integration.updateConfig(config);
        integration.setEnabled(true);
        integrationRepository.save(integration);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize integration config", e);
        }
    }

    public String extractField(String json, String field) {
        try {
            return objectMapper.readTree(json).get(field).asText();
        } catch (Exception e) {
            log.error("Failed to extract {} from integration config", field);
            return null;
        }
    }
}
