package fr.sanglierlab.traveltracker.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.7";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);
    }

    @Test
    void bloqueApresLeNombreMaxDEchecs() {
        service.recordFailure(IP);
        service.recordFailure(IP);
        assertThat(service.isBlocked(IP)).isFalse();

        service.recordFailure(IP);
        assertThat(service.isBlocked(IP)).isTrue();
    }

    @Test
    void debloqueApresLaDureeDeVerrouillage() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(IP);
        }
        clock.advance(Duration.ofMinutes(16));
        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void uneConnexionReussieRemetLeCompteurAZero() {
        service.recordFailure(IP);
        service.recordFailure(IP);
        service.recordSuccess(IP);
        service.recordFailure(IP);
        assertThat(service.isBlocked(IP)).isFalse();
    }

    @Test
    void lesIpSontIndependantes() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(IP);
        }
        assertThat(service.isBlocked("198.51.100.1")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
