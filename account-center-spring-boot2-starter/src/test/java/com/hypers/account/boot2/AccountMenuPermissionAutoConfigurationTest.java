package com.hypers.account.boot2;

import com.hypers.account.integration.menu.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class AccountMenuPermissionAutoConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(Consumer.class);

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Consumer { }

    private WebApplicationContextRunner enabled() {
        return runner.withPropertyValues("account.integration.menu-permission.enabled=true",
                "account.integration.menu-permission.app-code=test-app",
                "account.integration.menu-permission.secret=test-only-secret");
    }

    private WebApplicationContextRunner complete() {
        return enabled().withBean(AccountMenuPermissionHandler.class, StubHandler::new)
                .withBean(AccountNonceStore.class, InMemoryAccountNonceStore::new);
    }

    @Test
    void discoversStarterThroughSpringFactoriesAndDefaultsToDisabled() {
        List<String> names = SpringFactoriesLoader.loadFactoryNames(EnableAutoConfiguration.class,
                getClass().getClassLoader());
        assertThat(names).contains(AccountMenuPermissionAutoConfiguration.class.getName());
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AccountMenuPermissionController.class);
            assertThat(context).doesNotHaveBean("accountMenuPermissionSignatureFilter");
        });
    }

    @Test
    void registersOnlyProtectedPathsAndRejectsUnsignedRequest() {
        complete().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AccountMenuPermissionController.class);
            FilterRegistrationBean<?> registration = context.getBean("accountMenuPermissionSignatureFilter",
                    FilterRegistrationBean.class);
            assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder(
                    AccountMenuPermissionProtocol.QUERY_PATH, AccountMenuPermissionProtocol.REPLACE_PATH);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", AccountMenuPermissionProtocol.QUERY_PATH);
            request.addHeader(AccountMenuPermissionProtocol.PROTOCOL_VERSION_HEADER, AccountMenuPermissionProtocol.VERSION);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            registration.getFilter().doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
        });
    }

    @Test
    void missingHandlerFailsStartup() {
        enabled().withBean(AccountNonceStore.class, InMemoryAccountNonceStore::new)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingNonceStoreFailsWithoutMemoryFallback() {
        enabled().withBean(AccountMenuPermissionHandler.class, StubHandler::new)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingSecretFailsStartup() {
        complete().withPropertyValues("account.integration.menu-permission.secret=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingAppCodeFailsStartup() {
        complete().withPropertyValues("account.integration.menu-permission.app-code=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidClockWindowFailsStartup() {
        for (String value : new String[] {"0", "-1", "300001"}) {
            complete().withPropertyValues("account.integration.menu-permission.allowed-clock-skew-millis=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    static class StubHandler implements AccountMenuPermissionHandler {
        public MenuPermissionSnapshot query(MenuPermissionQuery query) { return new MenuPermissionSnapshot(); }
        public MenuPermissionSnapshot replace(String key, MenuPermissionReplaceCommand command) {
            return new MenuPermissionSnapshot();
        }
    }
}
