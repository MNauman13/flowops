package com.flowops.process;

import java.math.BigDecimal;
import java.util.Objects;

public record EventAmount(BigDecimal value, String unit) {
    public EventAmount {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        if (unit.isEmpty()) {
            throw new IllegalArgumentException("Amount unit must not be empty");
        }
    }
}
