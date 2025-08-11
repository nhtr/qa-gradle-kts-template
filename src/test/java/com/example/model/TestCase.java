package com.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCase {
    public String id;
    public Map<String, Double> input;
    public Double expected;
    public String raises;
}
