#!/usr/bin/env python3
"""Plot X-View evidence JSON as a static scatter PNG; invalid/empty input is fatal."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

# Direct CLI invocation also needs the repository root on sys.path.
import sys
if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from scripts.analysis import parse_time, InvalidExperiment


def validated_data(path: Path):
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict) or not isinstance(raw.get("metadata"), dict):
        raise InvalidExperiment("X-View JSON needs an object with metadata")
    metadata = raw["metadata"]
    if not isinstance(metadata.get("experiment"), str) or not metadata["experiment"]:
        raise InvalidExperiment("metadata.experiment missing")
    if not isinstance(metadata.get("apmEnabled"), bool):
        raise InvalidExperiment("metadata.apmEnabled must be bool")
    parse_time(metadata.get("capturedAt"))
    data = raw.get("transactions")
    if not isinstance(data, list) or not data:
        raise InvalidExperiment("transactions must be a nonempty array")
    ids = set()
    plotted = []
    for idx, row in enumerate(data, 1):
        if not isinstance(row, dict) or not isinstance(row.get("id"), str) or not row["id"]:
            raise InvalidExperiment(f"transaction #{idx}: id missing")
        if row["id"] in ids:
            raise InvalidExperiment(f"transaction #{idx}: duplicate id")
        ids.add(row["id"])
        started = parse_time(row.get("startedAt"))
        ms = row.get("elapsedMs")
        if isinstance(ms, bool) or not isinstance(ms, (int, float)) or not math.isfinite(ms) or ms < 0:
            raise InvalidExperiment(f"transaction #{idx}: elapsedMs must be finite >= 0")
        plotted.append((started, ms))
    return metadata, plotted


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        metadata, entries = validated_data(args.input)
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import matplotlib.dates as mdates
        fig, ax = plt.subplots(figsize=(12, 5), layout="constrained")
        normal = [(t, ms) for t, ms in entries if ms < 500]
        slow = [(t, ms) for t, ms in entries if ms >= 500]
        if normal:
            ax.scatter([t for t, _ in normal], [ms for _, ms in normal], s=16, label="Normal (<500 ms)")
        if slow:
            ax.scatter([t for t, _ in slow], [ms for _, ms in slow], marker="x", s=40, label="Slow (>=500 ms)")
        ax.axhline(500, linestyle="--", linewidth=1, label="Slow threshold (500 ms)")
        ax.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M:%S", tz=__import__("datetime").timezone.utc))
        ax.set_xlabel("Request start (UTC)")
        ax.set_ylabel("Spring MVC handler processing time (ms)")
        ax.set_title(f"coupon-apm-lab X-View — {metadata['experiment']}")
        ax.legend()
        ax.grid(alpha=0.3)
        fig.autofmt_xdate()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        fig.savefig(args.output, dpi=140)
        plt.close(fig)
        print(f"Created X-View PNG ({len(entries)} points): {args.output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, ImportError) as exc:
        parser.exit(2, f"Cannot generate X-View evidence: {exc}\n")


if __name__ == "__main__":
    raise SystemExit(main())
