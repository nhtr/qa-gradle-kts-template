package com.example.util;

import io.qameta.allure.Allure;

import java.util.Map;

public class TestUtil {
    public static void addParamsRecursive(String prefix, Object value, int depthLimit) {
        if (value == null) {
            Allure.parameter(prefix, "null");
            return;
        }
        if (depthLimit <= 0) {
            Allure.parameter(prefix, String.valueOf(value));
            return;
        }
        if (value instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                addParamsRecursive(prefix.isEmpty() ? key : prefix + "." + key, e.getValue(), depthLimit - 1);
            }
        } else if (value instanceof Iterable<?> it) {
            int i = 0;
            for (var v : it) {
                addParamsRecursive(prefix + "[" + i++ + "]", v, depthLimit - 1);
            }
        } else if (value.getClass().isArray()) {
            Object[] arr = (Object[]) value;
            for (int i = 0; i < arr.length; i++) {
                addParamsRecursive(prefix + "[" + i + "]", arr[i], depthLimit - 1);
            }
        } else {
            Allure.parameter(prefix, String.valueOf(value));
        }
    }
}
