# Purchase-to-payment fixtures

These are synthetic canonical cases for the Phase 0 expectation review. They use GBP amounts and illustrative activity codes. Review the XES activity mappings and amount semantics separately before enabling real-data rules.

The `events` array is in arrival order. Each checkpoint evaluates the first `afterArrivals` events at its fixed `asOf` time. Check the canonical event order against `orderedEventIds`. Check only the listed `projection` and `ruleResults` fields. The fixture checks in CI validate input and checkpoint integrity. The Java reducer and rule tests will compare actual outputs to these hand-authored expectations.

| Case | Behaviour to verify |
| --- | --- |
| GR-based | An out-of-order receipt supplies missing evidence and clears the two rule failures. The later partial reversal reduces the received total to GBP 90. |
| Non-GR-based | Invoice before receipt is allowed. The first partial receipt establishes no mismatch. Confirm that this category produces no invoice-before-receipt finding. |
| Two-way | Waiting crosses the threshold only after 72 hours. The case can clear without a receipt. |
| Consignment | Missing invoice evidence produces no purchase-item missing-invoice finding. Invoice matching remains unknown. |

The illustrative mapping treats the purchase order as substantive activity and accepts the explicit negative receipt delta with its reversal reference. These expectations depend on that mapping.