import json
import tempfile
import unittest
from pathlib import Path

from scripts.analysis import InvalidExperiment
from scripts.collect_xview import convert_xview
from scripts.plot_xview import validated_data

META = {"experiment": "r_test", "apmEnabled": True, "capturedAt": "2026-11-08T11:00:00+00:00"}
RAW = [{"transactionId": "a", "httpMethod": "POST",
        "routePattern": "/api/coupons/{couponId}/issues", "startedAt": "2026-11-08T10:59:00Z",
        "elapsedMs": 500, "statusCode": 201}]


class XviewTest(unittest.TestCase):
    def test_raw_to_evidence_preserves_id_start_time_duration(self):
        converted = convert_xview(RAW, META)
        self.assertEqual(converted["transactions"],
                         [{"id": "a", "startedAt": "2026-11-08T10:59:00Z", "elapsedMs": 500}])
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "xview.json"
            target.write_text(json.dumps(converted))
            metadata, records = validated_data(target)
            self.assertEqual(len(records), 1)
            self.assertEqual(records[0][1], 500)

    def test_duplicate_missing_id_and_negative_ms_fail(self):
        for bad in ([*RAW, *RAW], [{**RAW[0], "elapsedMs": -1}],
                    [{**RAW[0], "transactionId": ""}]):
            with self.subTest(bad=bad):
                with self.assertRaises(InvalidExperiment):
                    convert_xview(bad, META)

    def test_empty_xview_refused_by_plot(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "empty.json"
            target.write_text(json.dumps({"metadata": META, "transactions": []}))
            with self.assertRaises(InvalidExperiment):
                validated_data(target)


if __name__ == "__main__":
    unittest.main()
