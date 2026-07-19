package com.devpulse.integrations;

import com.devpulse.shared.slack.SlackClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    @Mock private TenantIntegrationRepository integrationRepository;
    @Mock private SlackClient slackClient;

    // ObjectMapper needs to be real (not mocked) because the service
    // uses it to serialize/deserialize JSON config strings
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configureSlack_whenTestMessageFails_returnsFalseAndDoesNotSave() {
        IntegrationService service =
                new IntegrationService(integrationRepository, slackClient, objectMapper);

        UUID tenantId = UUID.randomUUID();
        when(slackClient.postMessage(anyString(), anyString())).thenReturn(false);

        boolean result = service.configureSlack(tenantId, "https://hooks.slack.com/bad-url");

        assertThat(result).isFalse();
        // Never saves a broken webhook URL
        verify(integrationRepository, never()).save(any());
    }

    @Test
    void configureSlack_whenTestMessageSucceeds_savesAndReturnsTrue() {
        IntegrationService service =
                new IntegrationService(integrationRepository, slackClient, objectMapper);

        UUID tenantId = UUID.randomUUID();
        String webhookUrl = "https://hooks.slack.com/services/T123/B456/abc";

        when(slackClient.postMessage(eq(webhookUrl), anyString())).thenReturn(true);
        when(integrationRepository.findByTenantIdAndIntegrationType(tenantId, "SLACK"))
                .thenReturn(Optional.empty());
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean result = service.configureSlack(tenantId, webhookUrl);

        assertThat(result).isTrue();
        verify(integrationRepository).save(any(TenantIntegration.class));
    }

    @Test
    void postStandupToSlack_whenNoSlackConfigured_returnsFalseWithoutCallingSlack() {
        IntegrationService service =
                new IntegrationService(integrationRepository, slackClient, objectMapper);

        UUID tenantId = UUID.randomUUID();
        when(integrationRepository.findByTenantIdAndIntegrationType(tenantId, "SLACK"))
                .thenReturn(Optional.empty());

        boolean result = service.postStandupToSlack(
                tenantId, "john", "2026-07-19", "* Did some work");

        assertThat(result).isFalse();
        // Slack client never called when not configured
        verify(slackClient, never()).postMessage(anyString(), anyString());
    }

    @Test
    void configureLinear_savesApiKeyCorrectly() {
        IntegrationService service =
                new IntegrationService(integrationRepository, slackClient, objectMapper);

        UUID tenantId = UUID.randomUUID();
        when(integrationRepository.findByTenantIdAndIntegrationType(tenantId, "LINEAR"))
                .thenReturn(Optional.empty());
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.configureLinear(tenantId, "lin_api_test_key_123");

        verify(integrationRepository).save(any(TenantIntegration.class));
    }

    @Test
    void isSlackConfigured_returnsTrueWhenExists() {
        IntegrationService service =
                new IntegrationService(integrationRepository, slackClient, objectMapper);

        UUID tenantId = UUID.randomUUID();
        when(integrationRepository.existsByTenantIdAndIntegrationType(tenantId, "SLACK"))
                .thenReturn(true);

        assertThat(service.isSlackConfigured(tenantId)).isTrue();
    }

    @Test
    void isLinearConfigured_returnsFalseWhenNotExists() {
        IntegrationService service =
                new IntegrationService(integrationRepository, slackClient, objectMapper);

        UUID tenantId = UUID.randomUUID();
        when(integrationRepository.existsByTenantIdAndIntegrationType(tenantId, "LINEAR"))
                .thenReturn(false);

        assertThat(service.isLinearConfigured(tenantId)).isFalse();
    }

    @Test
    void getAllLinearIntegrations_returnsOnlyEnabledLinearIntegrations() {
        IntegrationService service =
                new IntegrationService(integrationRepository, slackClient, objectMapper);

        TenantIntegration linearEnabled =
                TenantIntegration.create(UUID.randomUUID(), "LINEAR", "{\"apiKey\":\"key1\"}");
        TenantIntegration slackEnabled =
                TenantIntegration.create(UUID.randomUUID(), "SLACK", "{\"webhookUrl\":\"url\"}");

        when(integrationRepository.findAll())
                .thenReturn(List.of(linearEnabled, slackEnabled));

        List<TenantIntegration> result = service.getAllLinearIntegrations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIntegrationType()).isEqualTo("LINEAR");
    }
}
