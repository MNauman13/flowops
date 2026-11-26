import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


EVENTS_DIR = Path(__file__).resolve().parent


def read_json(relative_path):
    return json.loads((EVENTS_DIR / relative_path).read_text(encoding="utf-8"))


class CanonicalEventTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        schema = read_json("canonical-event.schema.json")
        Draft202012Validator.check_schema(schema)
        cls.validator = Draft202012Validator(
            schema, format_checker=Draft202012Validator.FORMAT_CHECKER
        )

    def test_accepts_goods_receipt(self):
        receipt = read_json("examples/goods-receipt.json")
        self.validator.validate(receipt)

    def test_rejects_invalid_timestamp_and_amount(self):
        receipt = read_json("examples/invalid-goods-receipt.json")
        errors = {
            (tuple(error.absolute_path), error.validator)
            for error in self.validator.iter_errors(receipt)
        }

        self.assertEqual(
            errors,
            {
                (("occurredAt",), "format"),
                (("amount", "value"), "pattern"),
            },
        )


if __name__ == "__main__":
    unittest.main()