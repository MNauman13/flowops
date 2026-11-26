import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


EVENTS_DIR = Path(__file__).resolve().parent


def read_json(relative_path):
    return json.loads((EVENTS_DIR / relative_path).read_text(encoding="utf-8"))


def make_validator(schema_path, **options):
    schema = read_json(schema_path)
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(
        schema, format_checker=Draft202012Validator.FORMAT_CHECKER, **options
    )


def validation_errors(validator, value):
    return {
        (tuple(error.absolute_path), error.validator)
        for error in validator.iter_errors(value)
    }


class CanonicalEventTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.validator = make_validator("canonical-event.schema.json")

    def test_accepts_goods_receipt(self):
        receipt = read_json("examples/goods-receipt.json")
        self.validator.validate(receipt)

    def test_rejects_invalid_timestamp_and_amount(self):
        receipt = read_json("examples/invalid-goods-receipt.json")
        self.assertEqual(
            validation_errors(self.validator, receipt),
            {
                (("occurredAt",), "format"),
                (("amount", "value"), "pattern"),
            },
        )


class EventBatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        event_schema = read_json("canonical-event.schema.json")
        registry = Registry().with_resource(
            (EVENTS_DIR / "canonical-event.schema.json").as_uri(),
            Resource.from_contents(event_schema),
        )
        batch_schema = read_json("event-batch.schema.json")
        batch_schema["$id"] = (EVENTS_DIR / "event-batch.schema.json").as_uri()
        Draft202012Validator.check_schema(batch_schema)
        cls.validator = Draft202012Validator(
            batch_schema,
            registry=registry,
            format_checker=Draft202012Validator.FORMAT_CHECKER,
        )

    def test_accepts_minimum_and_maximum_batch_sizes(self):
        receipt = read_json("examples/goods-receipt.json")
        for count in (1, 1000):
            with self.subTest(count=count):
                self.validator.validate({"events": [receipt] * count})

    def test_rejects_batch_sizes_outside_limits(self):
        receipt = read_json("examples/goods-receipt.json")
        for count, keyword in ((0, "minItems"), (1001, "maxItems")):
            with self.subTest(count=count):
                batch = {"events": [receipt] * count}
                self.assertEqual(
                    validation_errors(self.validator, batch),
                    {(("events",), keyword)},
                )

    def test_checks_nested_event_fields(self):
        valid = read_json("examples/goods-receipt.json")
        invalid = read_json("examples/invalid-goods-receipt.json")
        self.assertEqual(
            validation_errors(self.validator, {"events": [valid, invalid]}),
            {
                (("events", 1, "occurredAt"), "format"),
                (("events", 1, "amount", "value"), "pattern"),
            },
        )


class EventAcceptedTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.validator = make_validator("event-accepted.v1.schema.json")

    def test_accepts_reference_message_and_additive_fields(self):
        message = read_json("examples/event-accepted.v1.json")
        self.validator.validate(message)
        message["producer"] = "flowops-worker"
        message["payload"]["traceId"] = "trace-123"
        self.validator.validate(message)

    def test_rejects_unsupported_version_and_invalid_event_id(self):
        message = read_json("examples/event-accepted.v1.json")
        message["schemaVersion"] = 2
        message["payload"]["eventId"] = "evt-1042"
        self.assertEqual(
            validation_errors(self.validator, message),
            {(("schemaVersion",), "const"), (("payload", "eventId"), "format")},
        )


if __name__ == "__main__":
    unittest.main()
