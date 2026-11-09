#!/usr/bin/env python3
"""Validate and regenerate ONLY existing measured evidence. Never synthesize results."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCK_WAIT = ROOT / "docs/evidence/lock-wait"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-dir", type=Path, default=LOCK_WAIT,
                        help="existing measured lock-wait evidence directory")
    args = parser.parse_args()
    folder = args.run_dir
    source = folder / "xview-raw.json"
    manifest = folder / "manifest.json"
    if not source.is_file() or not manifest.is_file():
        print(f"BLOCKED: real raw X-View JSON and Manifest required: {source}, {manifest}", file=sys.stderr)
        return 2
    for script, output, extras in (
        ("collect_xview.py", folder / "xview.json", ["--manifest", str(manifest)]),
        ("plot_xview.py", folder / "xview.png", []),
    ):
        command = [sys.executable, str(ROOT / "scripts" / script),
                   "--input", str(source if script == "collect_xview.py" else folder / "xview.json"),
                   "--output", str(output), *extras]
        result = subprocess.run(command, cwd=ROOT, check=False)
        if result.returncode:
            return result.returncode
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
