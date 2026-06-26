package com.devpulse.shared.security;

import com.devpulse.tenant.ApiKeyService;
import com.devpulse.tenant.Tenant;
import com.devpulse.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests using "Authorization: Bearer dp_live_{keyId}.{keySecret}".
 *
 * Unlike a login-then-JWT flow, B2B APIs like Stripe and GitHub send the API
 * key itself on every request — no separate token to expire or refresh.
 * Simpler for server-to-server calls, which is what this API is for.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TenantRepository tenantRepository;
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String rawKey = authHeader.substring(BEARER_PREFIX.length());
            ApiKeyService.ParsedApiKey parsed = apiKeyService.parse(rawKey);

            if (parsed != null) {
                Optional<Tenant> tenantMatch = tenantRepository.findByKeyId(parsed.keyId());

                if (tenantMatch.isPresent()
                        && apiKeyService.secretMatches(parsed.keySecret(), tenantMatch.get().getApiKeyHash())) {

                    var authentication = new UsernamePasswordAuthenticationToken(tenantMatch.get().getId(), null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}