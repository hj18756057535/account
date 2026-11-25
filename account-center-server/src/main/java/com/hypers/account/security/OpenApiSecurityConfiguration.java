package com.hypers.account.security;

import com.hypers.account.app.service.ApplicationManagementService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiSecurityConfiguration {

    @Bean
    public InMemoryNonceStore inMemoryNonceStore(Clock clock) {
        return new InMemoryNonceStore(clock);
    }

    @Bean
    public OpenApiSignatureVerifier openApiSignatureVerifier(
            ApplicationManagementService applicationService,
            HmacSignatureService signatureService,
            InMemoryNonceStore nonceStore,
            Clock clock) {
        return new OpenApiSignatureVerifier(applicationService, signatureService, nonceStore, clock);
    }
}
