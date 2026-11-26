import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


FIXTURES_DIR = Path(__file__).resolve().parent
CANONICAL_SCHEMA = FIXTURES_DIR.parents[1] / "contracts/events/canonical-event.schema.json"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


class PurchaseToPaymentFixturesTest(unittest.TestCase):
    def test_fixture_inputs_and_checkpoints(self):
        validator = Draft202012Validator(
            read_json(CANONICAL_SCHEMA),
            format_checker=Draft202012Validator.FORMAT_CHECKER,
        )
        categories = set()
        for path in sorted(FIXTURES_DIR.glob("*.json")):
            with self.subTest(fixture=path.name):
                fixture = read_json(path)
                events = fixture["events"]
                self.assertTrue(events)
                self.assertTrue(fixture["checkpoints"])
                categories.add(fixture["flowCategory"])
                self.assertEqual(
                    events[0]["attributes"]["flowCategory"], fixture["flowCategory"]
                )
                for event in events:
                    validator.validate(event)
                    self.assertEqual(event["caseKey"], events[0]["caseKey"])
                self.assertEqual(
                    len({event["externalEventId"] for event in events}), len(events)
                )
                self.assertEqual(
                    len({event["sourceSequence"] for event in events}), len(events)
                )

                for checkpoint in fixture["checkpoints"]:
                    with self.subTest(checkpoint=checkpoint["name"]):
                        count = checkpoint["afterArrivals"]
                        self.assertLessEqual(1, count)
                        self.assertLessEqual(count, len(events))
                        self.assertCountEqual(
                            checkpoint["orderedEventIds"],
                            [event["externalEventId"] for event in events[:count]],
                        )

        self.assertEqual(
            categories, {"THREE_WAY_GR_BASED", "THREE_WAY_NON_GR_BASED", "TWO_WAY", "CONSIGNMENT"}
        )


if __name__ == "__main__":
    unittest.main()