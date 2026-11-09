#!/usr/bin/env python3
"""Convert raw GET /api/apm/xview ARRAY to evidence object, verifying no record loss."""
from __future__ import annotations

import argparse
import json
import math
from datetime import datetime, timezone
from pathlib import Path

# Direct CLI invocation also needs the repository root on sys.path.
import sys
if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts.analysis import parse_time, InvalidExperiment


def convert_xview(raw: list[dict], metadata: dict) -> dict:
    if not isinstance(raw, list):
        raise InvalidExperiment("raw X-View must be a JSON array")
    if not isinstance(metadata.get("experiment"), str) or not metadata["experiment"]:
        raise InvalidExperiment("metadata.experiment must be a nonempty string")
    if not isinstance(metadata.get("apmEnabled"), bool):
        raise InvalidExperiment("metadata.apmEnabled must be a boolean")
    parse_time(metadata.get("capturedAt"))
    seen: set[str] = set()
    entries: list[dict] = []
    for index, item in enumerate(raw, 1):
        if not isinstance(item, dict):
            raise InvalidExperiment(f"X-View record {index} is not an object")
        trace_id = item.get("transactionId")
        if not isinstance(trace_id, str) or not trace_id:
            raise InvalidExperiment(f"X-View record {index} missing transactionId")
        if trace_id in seen:
            raise InvalidExperiment(f"duplicate X-View transaction ID {trace_id}")
        seen.add(trace_id)
        start = item.get("startedAt")
        parse_time(start)
        elapsed = item.get("elapsedMs")
        if isinstance(elapsed, bool) or not isinstance(elapsed, (int, float)):
            raise InvalidExperiment(f"X-View record {index} elapsedMs must be numeric")
        if not math.isfinite(elapsed) or elapsed < 0:
            raise InvalidExperiment(f"X-View record {index} negative or nonfinite elapsedMs")
        entries.append({"id": trace_id, "startedAt": start, "elapsedMs": elapsed})
    if len(entries) != len(raw) or len(seen) != len(raw):
        raise InvalidExperiment("X-View record count or identity mismatch")
    return {"metadata": metadata, "transactions": entries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        raw = json.loads(args.input.read_text(encoding="utf-8"))
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        metadata = {"experiment": manifest["runId"], "apmEnabled": manifest["apmEnabled"],
                    "capturedAt": manifest["capturedAt"]}
        converted = convert_xview(raw, metadata)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(converted, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Verified and converted {len(raw)} X-View samples: {args.output}")
        return 0
    except (OSError, KeyError, ValueError, TypeError, json.JSONDecodeError) as exc:
        parser.exit(2, f"Invalid X-View evidence: {exc}\n")


if __name__ == "__main__":
    raise SystemExit(main())
