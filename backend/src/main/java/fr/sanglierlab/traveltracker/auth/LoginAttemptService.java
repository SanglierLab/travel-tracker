package fr.sanglierlab.traveltracker.auth;

import fr.sanglierlab.traveltracker.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation des échecs de connexion par adresse IP, en mémoire.
 * Après N échecs, l'IP est bloquée pendant une durée donnée. Un redémarrage remet tout à zéro.
 */
@Component
public class LoginAttemptService {

    private record State(int failures, Instant lastFailure, Instant lockedUntil) {
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(AppProperties props) {
        this(props.maxLoginFailures(), props.loginLockDuration(), Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration lockDuration, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public boolean isBlocked(String ip) {
        State s = states.get(ip);
        return s != null && s.lockedUntil() != null && clock.instant().isBefore(s.lockedUntil());
    }

    public void recordFailure(String ip) {
        Instant now = clock.instant();
        states.values().removeIf(s -> isStale(s, now));
        states.compute(ip, (key, s) -> {
            int failures = (s == null || isStale(s, now)) ? 1 : s.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockDuration) : null;
            return new State(failures, now, lockedUntil);
        });
    }

    public void recordSuccess(String ip) {
        states.remove(ip);
    }

    private boolean isStale(State s, Instant now) {
        return now.isAfter(s.lastFailure().plus(lockDuration));
    }
}
