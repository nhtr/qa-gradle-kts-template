package com.example;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class TokenService {
    private final Clock clock;

    public TokenService(Clock clock) {
        this.clock = clock;
    }

    public boolean isExpired(Instant issuedAt, Duration ttl) {
        Instant now = Instant.now(clock);
        return now.isAfter(issuedAt.plus(ttl));
    }
}
