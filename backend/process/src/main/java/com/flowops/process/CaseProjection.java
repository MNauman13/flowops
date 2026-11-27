package com.flowops.process;

import java.time.Instant;
import java.util.List;

/**
 * Observations calculated from the accepted events for one purchase line item.
 * A null amount means a reliable value could not be calculated.
 */
public record CaseProjection(
        CaseKey caseKey,
        List<String> orderedEventIds,
        FlowCategory flowCategory,
        EventAmount expectedAmount,
        EventAmount receivedAmount,
        EventAmount invoicedAmount,
        Instant lastActivityAt,
        boolean clearingObserved,
        boolean cancellationObserved
) {
    public CaseProjection {
        orderedEventIds = List.copyOf(orderedEventIds);
    }
}
