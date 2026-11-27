package com.flowops.process;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public final class CaseReducer {

    public List<CaseEvent> orderEvents(List<CaseEvent> events) {
        List<CaseEvent> ordered = new ArrayList<>(events);

        ordered.sort(
                Comparator.comparing(CaseEvent::occurredAt)
                        .thenComparingLong(CaseEvent::sourceSequence)
        );

        return ordered;
    }

    /**
     * Rebuilds a case from its accepted events. The source mapping supplies signed
     * deltas after interpreting the source's receipt, invoice and reversal conventions.
     */
    public CaseProjection reduce(CaseKey caseKey, List<CaseEvent> events) {
        Objects.requireNonNull(caseKey, "caseKey");
        List<CaseEvent> ordered = orderEvents(events);
        List<String> eventIds = new ArrayList<>(ordered.size());
        EnumSet<FlowCategory> categories = EnumSet.noneOf(FlowCategory.class);

        EventAmount expected = null;
        AmountTotal received = new AmountTotal();
        AmountTotal invoiced = new AmountTotal();
        boolean clearingObserved = false;
        boolean cancellationObserved = false;

        for (CaseEvent event : ordered) {
            if (!event.caseKey().equals(caseKey)) {
                throw new IllegalArgumentException("Event belongs to a different case: " + event.externalEventId());
            }
            eventIds.add(event.externalEventId());
            if (event.flowCategory() != null) {
                categories.add(event.flowCategory());
            }

            switch (event.eventType()) {
                case PO_VALUE_SET -> expected = event.amount();
                case RECEIPT_DELTA -> received.add(event.amount());
                case INVOICE_DELTA -> invoiced.add(event.amount());
                case CLEARING_RECORDED -> clearingObserved = true;
                case CASE_CANCELLED -> cancellationObserved = true;
                case ACTIVITY_OBSERVED -> { }
            }
        }

        FlowCategory category = categories.size() == 1 ? categories.iterator().next() : null;
        return new CaseProjection(
                caseKey, eventIds, category, expected, received.amount, invoiced.amount,
                ordered.isEmpty() ? null : ordered.getLast().occurredAt(),
                clearingObserved, cancellationObserved
        );
    }

    private static final class AmountTotal {
        private EventAmount amount;
        private boolean incomplete;

        private void add(EventAmount delta) {
            if (incomplete) {
                return;
            }
            if (delta == null || (amount != null && !amount.unit().equals(delta.unit()))) {
                incomplete = true;
                amount = null;
                return;
            }

            amount = amount == null
                    ? delta
                    : new EventAmount(amount.value().add(delta.value()), amount.unit());
        }
    }
}
