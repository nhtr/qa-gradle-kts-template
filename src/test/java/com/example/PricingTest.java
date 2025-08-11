package com.example;

import com.example.model.TestCase;
import com.example.util.TestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingTest {
    static Stream<TestCase> casesProvider() throws Exception {
        ObjectMapper om = new ObjectMapper();
        try (InputStream is = PricingTest.class.getResourceAsStream("/data/discount_cases.json")) {
            TestCase[] arr = om.readValue(is, TestCase[].class);
            return Arrays.stream(arr);
        }
    }

    @Epic("Pricing Module")
    @Feature("Discount Calculation")
    @Story("Calculate discount with parameterized JSON data")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("calculateDiscount - data-driven from JSON")
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("casesProvider")
    @SuppressWarnings("unchecked")
    void testCalculateDiscount(TestCase c) throws Exception {
        Allure.parameter("caseId", c.id);
        TestUtil.addParamsRecursive("input", c.input, 6);
        String status = "PASS";
        String error = null;
        Double actual;
        try {
            double price = c.input.get("price");
            double rate = c.input.get("rate");
            if (c.raises != null && !c.raises.isBlank()) {
                Class<?> exClass = Class.forName(c.raises);
                assertThrows((Class<? extends Throwable>) exClass, () -> Pricing.calculateDiscount(price, rate));
                return;
            }
            actual = Pricing.calculateDiscount(price, rate);
            assertEquals(c.expected, actual);
        } catch (AssertionError | Exception e) {
            status = "FAIL";
            error = e.getMessage();
            System.out.println("Test error: " + status + " message: " + error);
            throw e;
        } finally {
            System.out.println("Test done: " + status + " message: " + error);
        }
    }
}
