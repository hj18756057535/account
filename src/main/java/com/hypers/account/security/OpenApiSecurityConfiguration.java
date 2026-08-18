package com.hypers.account.security;

import com.hypers.account.app.AccountDirectoryService;
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
            AccountDirectoryService directoryService,
            HmacSignatureService signatureService,
            InMemoryNonceStore nonceStore,
            Clock clock) {
        return new OpenApiSignatureVerifier(directoryService, signatureService, nonceStore, clock);
    }
}
