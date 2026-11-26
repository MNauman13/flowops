import json
import unittest
from pathlib import Path
from urllib.parse import urljoin

from jsonschema import Draft202012Validator
from referencing import Registry
from referencing.jsonschema import DRAFT202012


CONTRACT_PATH = Path(__file__).resolve().with_name("ingestion.openapi.json")
EVENTS_DIR = CONTRACT_PATH.parents[1] / "events"
INGESTION_PATH = "/api/v1/tenants/{tenantId}/workspaces/{workspaceId}/sources/{sourceId}/events"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


class IngestionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        contract = read_json(CONTRACT_PATH)
        cls.operation = contract["paths"][INGESTION_PATH]["post"]
        registry = Registry().with_resource(
            CONTRACT_PATH.as_uri(), DRAFT202012.create_resource(contract)
        )
        for filename in ("event-batch.schema.json", "canonical-event.schema.json"):
            path = EVENTS_DIR / filename
            registry = registry.with_resource(
                path.as_uri(), DRAFT202012.create_resource(read_json(path))
            )
        cls.registry = registry

    def validator_for(self, schema):
        target = urljoin(CONTRACT_PATH.as_uri(), schema["$ref"])
        return Draft202012Validator(
            {"$ref": target},
            registry=self.registry,
            format_checker=Draft202012Validator.FORMAT_CHECKER,
        )

    def test_request_resolves_shared_batch_and_event_schemas(self):
        media = self.operation["requestBody"]["content"]["application/json"]
        validator = self.validator_for(media["schema"])
        valid = read_json(EVENTS_DIR / "examples/goods-receipt.json")
        invalid = read_json(EVENTS_DIR / "examples/invalid-goods-receipt.json")

        validator.validate({"events": [valid]})
        errors = {
            (tuple(error.absolute_path), error.validator)
            for error in validator.iter_errors({"events": [valid, invalid]})
        }
        self.assertEqual(
            errors,
            {
                (("events", 1, "occurredAt"), "format"),
                (("events", 1, "amount", "value"), "pattern"),
            },
        )

    def test_acceptance_example_matches_response_and_counts(self):
        media = self.operation["responses"]["202"]["content"]["application/json"]
        example = media["example"]
        self.validator_for(media["schema"]).validate(example)
        self.assertEqual(
            example["newCount"] + example["duplicateCount"],
            len(example["eventIds"]),
        )


if __name__ == "__main__":
    unittest.main()
