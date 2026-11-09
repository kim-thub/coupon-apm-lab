import csv
import tempfile
import unittest
from datetime import datetime, timezone, timedelta
from pathlib import Path

from scripts.analysis import (InvalidExperiment, nearest_rank_p95, validate_phase,
                              compare_off_on, read_requests)


class AnalysisTest(unittest.TestCase):
    def test_nearest_rank_p95_not_interpolated(self):
        self.assertEqual(nearest_rank_p95(list(range(1, 101))), 95)
        self.assertEqual(nearest_rank_p95([2, 4, 6]), 6)
        with self.assertRaises(InvalidExperiment):
            nearest_rank_p95([])

    def test_commit_cross_check_and_60_second_boundary(self):
        base = datetime(2026, 11, 8, tzinfo=timezone.utc)
        t = lambda seconds: (base + timedelta(seconds=seconds)).isoformat()
        alloc = {"runId": "run1", "phase": "measure", "users": [
            {"virtualUserIndex": 0, "userId": "run1_measure_u000", "couponIds": [10, 11]}
        ]}
        rows = [
            {"runId": "run1", "phase": "measure", "virtualUserIndex": "0",
             "userId": "run1_measure_u000", "couponId": str(coupon),
             "startedAt": t(start), "completedAt": t(done),
             "httpStatus": "201", "responseTimeMs": "10", "success": "true", "error": ""}
            for coupon, start, done in [(10, 1, 1.01), (11, 59.9, 60.1)]
        ]
        writer = {"runId": "run1", "phase": "measure", "windowStart": t(0),
                  "recordsCount": 2, "locustCount": 2, "errors": []}
        valid = validate_phase("run1", "measure", rows, writer, alloc,
                               [(10, "run1_measure_u000"), (11, "run1_measure_u000")], expected_users=1)
        self.assertEqual(valid["validity"], "VALID")
        self.assertEqual(valid["http201InWindow"], 1)
        self.assertEqual(valid["successTps"], 1 / 60)
        self.assertEqual(len(valid["boundaryRequests"]), 1)
        invalid = validate_phase("run1", "measure", rows, writer, alloc, [], expected_users=1)
        self.assertEqual(invalid["validity"], "INVALID")
        self.assertIn("DB commit cross-check", " ".join(invalid["reasons"]))

    def test_compare_medians_and_variance_not_hidden(self):
        rows = []
        for mode, values in [(False, [(100, 10), (101, 10), (180, 7)]),
                             (True, [(110, 9.5), (111, 9.4), (112, 9.6)])]:
            for idx, (p95, tps) in enumerate(values):
                rows.append({"runId": f"{mode}-{idx}", "phase": "measure", "validity": "VALID",
                             "apmEnabled": mode, "p95Ms": p95, "successTps": tps})
        result = compare_off_on(rows)
        self.assertEqual(result["target"], "PASS")
        self.assertEqual(result["variance"], "REVIEW_REQUIRED")
        self.assertAlmostEqual(result["p95IncreasePercent"], (111 / 101 - 1) * 100)
        with self.assertRaises(InvalidExperiment):
            compare_off_on(rows[1:])


if __name__ == "__main__":
    unittest.main()
