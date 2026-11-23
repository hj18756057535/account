package com.hypers.account.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SsoTicketServiceTest {

    private static final AccountUserSnapshot USER =
            new AccountUserSnapshot("u-1", "zhangsan", "zhangsan@example.com", "张三", "13800000000", "default");

    @Test
    void concurrentExchangeConsumesTicketExactlyOnce() throws Exception {
        SsoTicketService service = new SsoTicketService(Clock.systemUTC(), Duration.ofMinutes(1));
        String code = service.issue("pilot", USER);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 32; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.exchange("pilot", code);
                        return true;
                    } catch (SsoTicketException exception) {
                        assertThat(exception).hasMessageContaining("already used");
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(5, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
    }

    @Test
    void mismatchDoesNotConsumeAndExactExpiryIsRejected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-16T00:00:00Z"));
        SsoTicketService service = new SsoTicketService(clock, Duration.ofSeconds(60));
        String code = service.issue("pilot", USER);
        assertThatThrownBy(() -> service.exchange("other", code)).isInstanceOf(SsoTicketException.class);
        assertThat(service.exchange("pilot", code).getExternalUserId()).isEqualTo("u-1");
        String expired = service.issue("pilot", USER);
        clock.advance(Duration.ofSeconds(60));
        assertThatThrownBy(() -> service.exchange("pilot", expired)).hasMessageContaining("expired");
    }

    @Test
    void exchangeReturnsUserPayloadOnlyOnceForMatchingApplication() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-05T00:00:00Z"));
        SsoTicketService service = new SsoTicketService(clock, Duration.ofSeconds(60));

        String code = service.issue("cms-ai", USER);
        SsoUserPayload payload = service.exchange("cms-ai", code);

        assertThat(payload.getExternalUserId()).isEqualTo("u-1");
        assertThat(payload.getAccount()).isEqualTo("zhangsan");
        assertThat(payload.getTenantCode()).isEqualTo("default");
        assertThatThrownBy(() -> service.exchange("cms-ai", code))
                .isInstanceOf(SsoTicketException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void exchangeRejectsExpiredOrApplicationMismatchedCode() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-05T00:00:00Z"));
        SsoTicketService service = new SsoTicketService(clock, Duration.ofSeconds(60));
        String code = service.issue("cms-ai", USER);

        assertThatThrownBy(() -> service.exchange("other-app", code))
                .isInstanceOf(SsoTicketException.class)
                .hasMessageContaining("application mismatch");

        String expiringCode = service.issue("cms-ai", USER);
        clock.advance(Duration.ofSeconds(61));

        assertThatThrownBy(() -> service.exchange("cms-ai", expiringCode))
                .isInstanceOf(SsoTicketException.class)
                .hasMessageContaining("expired");
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
