package com.devpulse.tenant;

import com.devpulse.tenant.dto.TenantRegistrationRequest;
import com.devpulse.tenant.dto.TenantRegistrationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void register_returnsFullPlaintextKeyExactlyOnce_andStoresOnlyTheHashedSecret() {
        var generated = new ApiKeyService.GeneratedApiKey(
                "fakeKeyId123", "fakeSecretValue", "dp_live_fakeKeyId123.fakeSecretValue"
        );
        String hashedSecret = "$2a$10$fakeHashValueForTesting";

        when(apiKeyService.generate()).thenReturn(generated);
        when(apiKeyService.hashSecret(generated.keySecret())).thenReturn(hashedSecret);
        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TenantRegistrationResponse response = tenantService.register(new TenantRegistrationRequest("Acme Corp"));

        assertThat(response.apiKey()).isEqualTo(generated.fullPlaintextKey());
        assertThat(response.name()).isEqualTo("Acme Corp");
    }
}