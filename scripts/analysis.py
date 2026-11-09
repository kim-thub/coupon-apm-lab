"""AC07 evidence calculations. Never silently exclude invalid requests or failed runs."""
from __future__ import annotations

import csv
import json
import math
import statistics
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


class InvalidExperiment(ValueError):
    """A recorded experiment cannot be treated as a valid AC07 sample."""


def parse_time(value: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (TypeError, ValueError) as exc:
        raise InvalidExperiment(f"invalid timestamp: {value!r}") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise InvalidExperiment(f"timestamp must have timezone: {value!r}")
    return parsed.astimezone(timezone.utc)


def nearest_rank_p95(values: list[float]) -> float:
    if not values:
        raise InvalidExperiment("p95 requires at least one HTTP 201 measurement")
    if any(not math.isfinite(x) or x < 0 for x in values):
        raise InvalidExperiment("invalid response time")
    return sorted(values)[math.ceil(0.95 * len(values)) - 1]


def read_requests(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        required = {"runId", "phase", "virtualUserIndex", "userId", "couponId",
                    "startedAt", "completedAt", "httpStatus", "responseTimeMs", "success", "error"}
        if not required.issubset(reader.fieldnames or []):
            raise InvalidExperiment(f"missing request CSV headers: {sorted(required - set(reader.fieldnames or []))}")
        return list(reader)


def validate_phase(run_id: str, phase: str, rows: list[dict], writer: dict,
                   allocations: dict, db_issues: list[tuple[int, str]],
                   expected_users: int = 100, duration_seconds: int = 60) -> dict:
    errors: list[str] = []
    if writer.get("runId") != run_id or writer.get("phase") != phase:
        errors.append("writer runId/phase mismatch")
    if allocations.get("runId") != run_id or allocations.get("phase") != phase:
        errors.append("allocation runId/phase mismatch")
    if writer.get("errors"):
        errors.extend(f"writer: {e}" for e in writer["errors"])
    if writer.get("recordsCount") != len(rows):
        errors.append(f"writer count {writer.get('recordsCount')} != CSV {len(rows)}")
    if writer.get("locustCount") != len(rows):
        errors.append(f"Locust count {writer.get('locustCount')} != CSV {len(rows)}")
    if not rows:
        errors.append("no request records")

    user_bundles = allocations.get("users", [])
    if len(user_bundles) != expected_users:
        errors.append(f"expected {expected_users} allocated users, got {len(user_bundles)}")
    valid_coupons: dict[int, tuple[int, str]] = {}
    for allocation in user_bundles:
        try:
            user_idx = int(allocation["virtualUserIndex"])
            user_id = allocation["userId"]
            for coupon_id in allocation["couponIds"]:
                key = int(coupon_id)
                if key in valid_coupons:
                    errors.append(f"duplicate allocated coupon: {key}")
                valid_coupons[key] = (user_idx, user_id)
        except (KeyError, ValueError, TypeError) as exc:
            errors.append(f"invalid allocation: {exc}")
    phase_start = parse_time(writer["windowStart"])
    from datetime import timedelta
    phase_end = phase_start + timedelta(seconds=duration_seconds)
    grace_end = phase_end + timedelta(seconds=15)

    user_counts: Counter[int] = Counter()
    status_codes: Counter[str] = Counter()
    seen_requests: set[tuple[int, str]] = set()
    successful_pairs: set[tuple[int, str]] = set()
    in_window = []
    boundary = []
    for position, row in enumerate(rows, 1):
        if row["runId"] != run_id or row["phase"] != phase:
            errors.append(f"row {position} runId/phase mismatch")
        try:
            user_index = int(row["virtualUserIndex"])
            coupon_id = int(row["couponId"])
            user_id = row["userId"]
            response_ms = float(row["responseTimeMs"])
            if not math.isfinite(response_ms) or response_ms < 0:
                errors.append(f"row {position} has invalid responseTimeMs")
            started = parse_time(row["startedAt"])
            completed = parse_time(row["completedAt"])
            if started > completed:
                errors.append(f"row {position} ends before it starts")
            if started < phase_start:
                errors.append(f"row {position} starts before phase")
            if completed > grace_end:
                errors.append(f"row {position} exceeded 15-second grace")
            if valid_coupons.get(coupon_id) != (user_index, user_id):
                errors.append(f"row {position} mismatches exclusive coupon allocation")
            pair = (coupon_id, user_id)
            if pair in seen_requests:
                errors.append(f"duplicate request coupon/user pair: {pair}")
            seen_requests.add(pair)
            status_codes[row["httpStatus"]] += 1
            success = row["httpStatus"] == "201" and row["success"].lower() == "true"
            if not success:
                errors.append(f"row {position} unexpected HTTP result: {row['httpStatus']} / {row['error']}")
            else:
                successful_pairs.add(pair)
            if completed <= phase_end:
                in_window.append((response_ms, success))
                if success:
                    user_counts[user_index] += 1
            else:
                boundary.append({"couponId": coupon_id, "userId": user_id, "completedAt": row["completedAt"]})
        except (KeyError, TypeError, ValueError, InvalidExperiment) as exc:
            errors.append(f"row {position} malformed: {exc}")

    actual_issues = [(int(coupon), user) for coupon, user in db_issues]
    committed = set(actual_issues)
    if len(actual_issues) != len(committed):
        errors.append("duplicate committed issues")
    if committed != successful_pairs:
        missing = successful_pairs - committed
        unexpected = committed - successful_pairs
        errors.append(f"DB commit cross-check mismatch: missing={len(missing)}, unexpected={len(unexpected)}")

    accepted_times = [ms for ms, ok in in_window if ok]
    success_total = len(accepted_times)
    if success_total == 0:
        errors.append("no HTTP 201 completed in measurement window")
    p95 = nearest_rank_p95(accepted_times) if accepted_times else None
    # 60-second denominator includes the ramp-up; whole HTTP RPS is a secondary diagnostic.
    return {
        "runId": run_id, "phase": phase, "validity": "INVALID" if errors else "VALID",
        "reasons": errors, "windowStart": phase_start.isoformat(), "windowEnd": phase_end.isoformat(),
        "http201InWindow": success_total, "httpRequestsInWindow": len(in_window),
        "statusCodesAll": dict(status_codes), "boundaryRequests": boundary,
        "p95Ms": p95, "successTps": success_total / duration_seconds,
        "httpRps": len(in_window) / duration_seconds,
        "committedIssues": len(actual_issues), "requestsRecorded": len(rows),
        "perUserSuccessInWindow": {str(i): user_counts[i] for i in range(expected_users)},
        "maxUserSuccess": max(user_counts.values(), default=0),
    }


def compare_off_on(validations: list[dict]) -> dict:
    valid = [v for v in validations if v.get("validity") == "VALID" and v.get("phase") == "measure"]
    off = [v for v in valid if v.get("apmEnabled") is False]
    on = [v for v in valid if v.get("apmEnabled") is True]
    if len(off) != 3 or len(on) != 3:
        raise InvalidExperiment(f"need exactly 3 valid OFF and 3 valid ON runs: OFF={len(off)} ON={len(on)}")
    med_off_p95 = statistics.median(x["p95Ms"] for x in off)
    med_on_p95 = statistics.median(x["p95Ms"] for x in on)
    med_off_tps = statistics.median(x["successTps"] for x in off)
    med_on_tps = statistics.median(x["successTps"] for x in on)
    if med_off_p95 <= 0 or med_off_tps <= 0:
        raise InvalidExperiment("OFF median requires positive p95 and TPS")
    p95_rise = (med_on_p95 / med_off_p95 - 1) * 100
    tps_drop = (1 - med_on_tps / med_off_tps) * 100
    review = False
    for group in (off, on):
        for attr in ("p95Ms", "successTps"):
            values = [v[attr] for v in group]
            if min(values) <= 0 or max(values) / min(values) > 1.15:
                review = True
    return {
        "validity": "VALID", "target": "PASS" if p95_rise <= 15 and tps_drop <= 15 else "FAIL",
        "variance": "REVIEW_REQUIRED" if review else "WITHIN_REVIEW_THRESHOLD",
        "offMedianP95Ms": med_off_p95, "onMedianP95Ms": med_on_p95,
        "offMedianTps": med_off_tps, "onMedianTps": med_on_tps,
        "p95IncreasePercent": p95_rise, "successTpsDecreasePercent": tps_drop,
        "runIds": [v["runId"] for v in valid],
    }


def write_comparison(path: Path, validations: list[dict], result: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(["runId", "apmEnabled", "validity", "p95Ms", "successTps", "httpRps"])
        for run in validations:
            writer.writerow([run["runId"], run.get("apmEnabled"), run.get("validity"),
                             run.get("p95Ms"), run.get("successTps"), run.get("httpRps")])
        writer.writerow([])
        for key, value in result.items():
            writer.writerow([key, value])
