package com.flowops.process;

import java.time.Instant;
import java.util.Objects;

public record CaseEvent(
        String externalEventId,
        long sourceSequence,
        CaseKey caseKey,
        Instant occurredAt,
        String activityCode,
        EventType eventType,
        EventAmount amount,
        FlowCategory flowCategory,
        EventReferences references
) {
    public CaseEvent {
        Objects.requireNonNull(externalEventId, "externalEventId");
        Objects.requireNonNull(caseKey, "caseKey");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(activityCode, "activityCode");
        Objects.requireNonNull(eventType, "eventType");

        if (externalEventId.isEmpty() || activityCode.isEmpty()) {
            throw new IllegalArgumentException("Event ID and activity code must not be empty");
        }
        if (sourceSequence < 0) {
            throw new IllegalArgumentException("Source sequence must be non-negative");
        }
    }
}
