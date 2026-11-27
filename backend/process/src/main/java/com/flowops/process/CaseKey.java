package com.flowops.process;

import java.util.Objects;

public record CaseKey(String document, String item) {
    public CaseKey {
        validatePart(document, "document");
        validatePart(item, "item");
    }

    private static void validatePart(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 128) {
            throw new IllegalArgumentException(name + " must contain 1 to 128 characters");
        }
    }
}
