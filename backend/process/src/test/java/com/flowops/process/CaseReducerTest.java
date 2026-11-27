package com.flowops.process;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseReducerTest {

    private static final CaseKey CHAIRS = new CaseKey("PO-100", "10");

    @Test
    void putsEarlierDeliveryBeforeInvoice() {
        CaseEvent invoice = new CaseEvent(
                "invoice-43", 3, CHAIRS, Instant.parse("2026-11-20T11:00:00Z"),
                "INVOICE_RECORDED", EventType.INVOICE_DELTA, null, null, null
        );
        CaseEvent delivery = new CaseEvent(
                "delivery-42", 2, CHAIRS, Instant.parse("2026-11-20T10:00:00Z"),
                "GOODS_RECEIPT_RECORDED", EventType.RECEIPT_DELTA, null, null, null
        );

        List<CaseEvent> arrivals = List.of(invoice, delivery);
        List<CaseEvent> ordered = new CaseReducer().orderEvents(arrivals);

        assertEquals(List.of(delivery, invoice), ordered);
    }

    @Test
    void usesSourceSequenceWhenTimestampsMatch() {
        Instant ten = Instant.parse("2026-11-20T10:00:00Z");

        CaseEvent delivery = new CaseEvent(
                "delivery-42", 2, CHAIRS, ten,
                "GOODS_RECEIPT_RECORDED", EventType.RECEIPT_DELTA, null, null, null
        );
        CaseEvent correction = new CaseEvent(
                "correction-43", 3, CHAIRS, ten,
                "RECEIPT_NOTE_UPDATED", EventType.ACTIVITY_OBSERVED, null, null, null
        );

        List<CaseEvent> arrivals = List.of(correction, delivery);
        List<CaseEvent> ordered = new CaseReducer().orderEvents(arrivals);

        assertEquals(List.of(delivery, correction), ordered);
    }

    @Test
    void rebuildsAmountsInBusinessOrderEvenWhenEventsArriveLate() {
        CaseEvent order = event(1, EventType.PO_VALUE_SET, amount("120.00", "GBP"));
        CaseEvent delivery = event(2, EventType.RECEIPT_DELTA, amount("80.00", "GBP"));
        CaseEvent revision = event(3, EventType.PO_VALUE_SET, amount("150.00", "GBP"));
        CaseEvent reversal = new CaseEvent(
                "event-4", 4, CHAIRS, atHour(4), "GOODS_RECEIPT_REVERSED",
                EventType.RECEIPT_DELTA, amount("-20.00", "GBP"), null,
                new EventReferences(null, null, delivery.externalEventId())
        );
        CaseEvent invoice = event(5, EventType.INVOICE_DELTA, amount("60.00", "GBP"));
        CaseEvent clearing = event(6, EventType.CLEARING_RECORDED, null);

        CaseReducer reducer = new CaseReducer();
        CaseProjection result = reducer.reduce(
                CHAIRS, List.of(revision, clearing, invoice, reversal, order, delivery)
        );

        assertEquals(List.of("event-1", "event-2", "event-3", "event-4", "event-5", "event-6"),
                result.orderedEventIds());
        assertEquals(amount("150.00", "GBP"), result.expectedAmount());
        assertEquals(amount("60.00", "GBP"), result.receivedAmount());
        assertEquals(amount("60.00", "GBP"), result.invoicedAmount());
        assertEquals(FlowCategory.THREE_WAY_GR_BASED, result.flowCategory());
        assertEquals(atHour(6), result.lastActivityAt());
        assertTrue(result.clearingObserved());
        assertFalse(result.cancellationObserved());
        assertEquals(
                result,
                reducer.reduce(CHAIRS, List.of(order, delivery, revision, reversal, invoice, clearing))
        );
    }

    @Test
    void leavesAmountsUnknownWhenEvidenceIsMissingOrUnitsAreMixed() {
        CaseProjection result = new CaseReducer().reduce(CHAIRS, List.of(
                event(1, EventType.PO_VALUE_SET, amount("120.00", "GBP")),
                event(2, EventType.PO_VALUE_SET, null),
                event(3, EventType.RECEIPT_DELTA, amount("25.00", "GBP")),
                event(4, EventType.RECEIPT_DELTA, amount("20.00", "EUR")),
                event(5, EventType.RECEIPT_DELTA, amount("5.00", "GBP")),
                event(6, EventType.INVOICE_DELTA, null),
                event(7, EventType.INVOICE_DELTA, amount("50.00", "GBP"))
        ));

        assertNull(result.expectedAmount());
        assertNull(result.receivedAmount());
        assertNull(result.invoicedAmount());
    }

    @Test
    void retainsAnExplicitZeroAndLeavesAnAbsentAmountUnknown() {
        CaseProjection result = new CaseReducer().reduce(CHAIRS, List.of(
                event(1, EventType.RECEIPT_DELTA, amount("0.00", "GBP")),
                event(2, EventType.CASE_CANCELLED, null)
        ));

        assertEquals(amount("0.00", "GBP"), result.receivedAmount());
        assertNull(result.invoicedAmount());
        assertTrue(result.cancellationObserved());
    }

    @Test
    void rejectsEventsFromAnotherPurchaseLine() {
        CaseKey desks = new CaseKey("PO-100", "20");
        CaseEvent chairDelivery = event(1, EventType.RECEIPT_DELTA, amount("80.00", "GBP"));

        assertThrows(IllegalArgumentException.class,
                () -> new CaseReducer().reduce(desks, List.of(chairDelivery)));
    }

    private static CaseEvent event(int sequence, EventType type, EventAmount amount) {
        return new CaseEvent(
                "event-" + sequence, sequence, CHAIRS, atHour(sequence),
                type.name(), type, amount, FlowCategory.THREE_WAY_GR_BASED, null
        );
    }

    private static Instant atHour(int hour) {
        return Instant.parse("2026-11-20T00:00:00Z").plusSeconds(3600L * hour);
    }

    private static EventAmount amount(String value, String unit) {
        return new EventAmount(new BigDecimal(value), unit);
    }
}
