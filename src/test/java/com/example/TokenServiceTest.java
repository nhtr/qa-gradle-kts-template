package com.example;

import com.example.util.QaCsvLogger;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenServiceTest {

    static Stream<Case> casesProvider() throws Exception {
        ObjectMapper om = new ObjectMapper();
        try (InputStream is = TokenServiceTest.class.getResourceAsStream("/data/cases_expire.json")) {
            Case[] arr = om.readValue(is, Case[].class);
            return Arrays.stream(arr);
        }
    }

    @Epic("Auth Module")
    @Feature("Token Expiration")
    @Story("Check expiration using fixed Clock")
    @DisplayName("isExpired - deterministic with fixed Clock")
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("casesProvider")
    void isExpired_param(Case c) throws Exception {
        Clock fixed = Clock.fixed(Instant.parse(c.now), ZoneOffset.UTC);
        TokenService svc = new TokenService(fixed);

        Instant issuedAt = Instant.parse(c.input.get("issuedAt"));
        Duration ttl = Duration.ofMinutes(Long.parseLong(c.input.get("ttlMinutes")));

        boolean actual = svc.isExpired(issuedAt, ttl);
        assertEquals(c.expected, actual);
        QaCsvLogger.log(c.id, c.input, c.expected, actual, "PASS", null);
        QaCsvLogger.save();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Case {
        public String id;
        public String now; // ISO-8601
        public Map<String, String> input; // issuedAt (ISO) & ttlMinutes (string)
        public boolean expected;
    }
}
