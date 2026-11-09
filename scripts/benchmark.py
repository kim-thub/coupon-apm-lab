#!/usr/bin/env python3
"""Run AC07: 6 prelim + 6 official 60s warmup/60s measurement sessions.

Requires Docker, Locust, PostgreSQL, Java/Docker image and Python requirements.
Never deletes the created run-owned databases automatically.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

# Direct CLI invocation also needs the repository root on sys.path.
if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

ROOT = Path(__file__).resolve().parent.parent
EVIDENCE = ROOT / "docs/evidence/overhead"
ORDER = [False, True, True, False, False, True]
PRELIM_COUPONS_PER_USER = 601
WARMUP_COUPONS_PER_USER = 601


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def save(path: Path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def command(argv, environment=None, log_path=None, check=True):
    print("COMMAND:", " ".join(map(str, argv)), flush=True)
    if log_path:
        with log_path.open("w", encoding="utf-8") as output:
            result = subprocess.run(argv, cwd=ROOT, env=environment,
                                    stdout=output, stderr=subprocess.STDOUT, check=False)
    else:
        result = subprocess.run(argv, cwd=ROOT, env=environment, check=False)
    if check and result.returncode != 0:
        raise RuntimeError(f"command failed exit={result.returncode}: {' '.join(map(str, argv))}; log={log_path}")
    return result.returncode


def compose(args, environment=None):
    env_file = ["--env-file", ".env"] if (ROOT / ".env").is_file() else []
    return ["docker", "compose", *env_file, *args], environment


def process_output(argv, env=None):
    return subprocess.check_output(argv, cwd=ROOT, env=env, text=True).strip()


def app_status(url, expected_mode, expected_db, timeout=120):
    import requests
    expires = time.monotonic() + timeout
    last = None
    while time.monotonic() < expires:
        try:
            response = requests.get(url + "/internal/benchmark/status", timeout=3)
            if response.ok:
                data = response.json()
                if data.get("ready") and data.get("databaseReady"):
                    if data.get("apmEnabled") is not expected_mode:
                        raise RuntimeError(f"APM mode mismatch: {data}")
                    if data.get("database") != expected_db:
                        raise RuntimeError(f"JDBC target database mismatch: {data}")
                    return data
        except requests.exceptions.RequestException as exc:
            last = str(exc)
        time.sleep(1)
    raise TimeoutError(f"application status not ready within {timeout}s: {last}")


def issue_smoke(url, expected_mode):
    import requests
    response = requests.post(url + "/api/coupons",
                             json={"name": "benchmark-smoke-" + uuid.uuid4().hex[:8], "totalStock": 1}, timeout=10)
    response.raise_for_status()
    if response.status_code != 201:
        raise RuntimeError("smoke coupon creation did not return 201")
    coupon_id = response.json()["id"]
    response = requests.post(url + f"/api/coupons/{coupon_id}/issues",
                             json={"userId": "smoke-" + uuid.uuid4().hex[:12]}, timeout=10)
    if response.status_code != 201:
        raise RuntimeError(f"smoke issue expected HTTP 201, got {response.status_code}: {response.text}")
    trace_url = url + "/api/apm/xview"
    until = time.monotonic() + 3
    while time.monotonic() < until:
        xview_response = requests.get(trace_url, timeout=5)
        xview_response.raise_for_status()
        data = xview_response.json()
        observed = any(t.get("statusCode") == 201 for t in data)
        if expected_mode and observed:
            return {"couponId": coupon_id, "apmRecordObserved": True}
        if not expected_mode:
            if data:
                raise RuntimeError("APM OFF generated X-View records")
            return {"couponId": coupon_id, "apmRecordObserved": False}
        time.sleep(0.05)
    raise RuntimeError("APM ON smoke issue did not generate an X-View sample")


def run_locust(url: str, run_id: str, phase: str, run_path: Path, allocation: Path):
    target = run_path / phase
    target.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env.update({"LOCUST_ALLOCATION_FILE": str(allocation),
                "EVIDENCE_PHASE_DIR": str(target),
                "EXPERIMENT_RUN_ID": run_id, "EXPERIMENT_PHASE": phase})
    argv = ["locust", "-f", "scripts/locustfile.py", "--headless", "--users", "100",
            "--spawn-rate", "10", "--run-time", "60s", "--stop-timeout", "15",
            "--host", url, "--csv", str(target / "locust"), "--only-summary"]
    exit_code = command(argv, environment=env, log_path=target / "locust.log", check=False)
    return exit_code


def collect_xview(url, run_path, run_id, enabled, captured_at):
    import requests
    raw = requests.get(url + "/api/apm/xview", timeout=10)
    raw.raise_for_status()
    data = raw.json()
    if not isinstance(data, list):
        raise RuntimeError("X-View API expected JSON array")
    save(run_path / "xview-raw.json", data)
    from scripts.collect_xview import convert_xview
    exported = convert_xview(data, {"experiment": run_id,
                                    "apmEnabled": enabled, "capturedAt": captured_at})
    save(run_path / "xview.json", exported)
    return len(data)


def run_one(batch: str, family: str, order_index: int, enabled: bool,
            measurement_coupons: int, image_id: str, git_commit: str, base_url: str,
            attempt: int = 0):
    from scripts.db import create_run_db, connect_db, seed_phase, issued_pairs, verify_database_schema
    from scripts.analysis import validate_phase, read_requests

    run_id = f"r_{batch}_{family}_{order_index:02d}_{'on' if enabled else 'off'}_{attempt:02d}"
    run_path = EVIDENCE / run_id
    run_path.mkdir(parents=True, exist_ok=False)
    manifest = {"runId": run_id, "family": family, "sequence": order_index,
                "apmEnabled": enabled, "applicationImageId": image_id,
                "gitCommit": git_commit, "startedAt": timestamp(), "status": "STARTED",
                "load": {"users": 100, "spawnRatePerSecond": 10, "durationSeconds": 60,
                         "waitBetweenRequestsSeconds": [0.1, 0.3], "stopGraceSeconds": 15},
                "warmupCouponsPerUser": WARMUP_COUPONS_PER_USER,
                "measureCouponsPerUser": measurement_coupons,
                "postgresVolumePolicy": "same PostgreSQL instance+volume; new run-owned database",
                "phaseDatabasePolicy": "warmup and measurement share the SAME app process and DB",
                "invalidReasons": []}
    save(run_path / "manifest.json", manifest)
    try:
        db_name = create_run_db(run_id)
        manifest["database"] = db_name
        save(run_path / "manifest.json", manifest)
        environment = os.environ.copy()
        environment.update({"DB_NAME": db_name, "APM_ENABLED": str(enabled).lower(),
                            "GIT_COMMIT": git_commit, "APP_PROFILE": "benchmark"})
        argv, environ = compose(["up", "-d", "--no-deps", "--force-recreate", "app"], environment)
        command(argv, environ, log_path=run_path / "server-start.log")
        manifest["appContainerId"] = process_output(["docker", "compose", "ps", "-q", "app"], environment)
        container_id = manifest["appContainerId"]
        actual_image = process_output(["docker", "inspect", "--format", "{{.Image}}", container_id])
        manifest["actualApplicationImageId"] = actual_image
        if actual_image != image_id:
            raise RuntimeError(f"application image identity differs from prebuilt image: {actual_image} != {image_id}")
        host_config = json.loads(process_output(["docker", "inspect", "--format", "{{json .HostConfig}}", container_id]))
        manifest["containerResources"] = {"nanoCpus": host_config.get("NanoCpus"),
                                         "memoryBytes": host_config.get("Memory")}
        container_env = json.loads(process_output(["docker", "inspect", "--format", "{{json .Config.Env}}", container_id]))
        # Never put passwords or secret-bearing environment variables into public evidence.
        manifest["runtimeEnvironment"] = {key: value for pair in container_env if "=" in pair
            for key, value in [pair.split("=", 1)]
            if key in {"JAVA_TOOL_OPTIONS", "SPRING_PROFILES_ACTIVE", "APM_ENABLED"}}
        manifest["statusBeforeWarmup"] = app_status(base_url, enabled, db_name)
        with connect_db(db_name) as db:
            schema = verify_database_schema(db)
            manifest.update(schema)
            with db.cursor() as cur:
                cur.execute("SHOW server_version")
                manifest["postgresVersion"] = cur.fetchone()[0]
        manifest["smoke"] = issue_smoke(base_url, enabled)
        save(run_path / "manifest.json", manifest)
        for phase, count in (("warmup", WARMUP_COUPONS_PER_USER), ("measure", measurement_coupons)):
            with connect_db(db_name) as db:
                allocations = seed_phase(db, run_id, phase, count)
            allocation_path = run_path / f"allocations-{phase}.json"
            save(allocation_path, allocations)
            manifest[f"{phase}CouponCount"] = allocations["seededCoupons"]
            save(run_path / "manifest.json", manifest)
            locust_exit = run_locust(base_url, run_id, phase, run_path, allocation_path)
            summary_path = run_path / phase / "writer_summary.json"
            if not summary_path.exists():
                raise RuntimeError(f"{phase} Locust session produced no writer summary; exit={locust_exit}")
            from scripts.analysis import read_requests, validate_phase
            rows = read_requests(run_path / phase / "requests.csv")
            writer = json.loads(summary_path.read_text(encoding="utf-8"))
            with connect_db(db_name) as db:
                committed = issued_pairs(db, run_id, phase)
            validation = validate_phase(run_id, phase, rows, writer, allocations, committed)
            validation["apmEnabled"] = enabled
            validation["locustExit"] = locust_exit
            if locust_exit:
                validation["reasons"].append(f"Locust exit code {locust_exit}")
                validation["validity"] = "INVALID"
            save(run_path / phase / "db-cross-check.json",
                 {"runId": run_id, "phase": phase, "committedIssues":
                  [{"couponId": c, "userId": u} for c, u in committed]})
            save(run_path / phase / "validation.json", validation)
            manifest[phase + "Validation"] = validation["validity"]
            if validation["validity"] != "VALID":
                manifest["invalidReasons"].extend(validation["reasons"])
                raise RuntimeError(f"{phase} INVALID: {validation['reasons'][:5]}")
            current_container = process_output(["docker", "compose", "ps", "-q", "app"], environment)
            if current_container != manifest["appContainerId"]:
                raise RuntimeError("Spring Boot container changed between warmup and measurement")
            if phase == "warmup":
                manifest["statusBetweenPhases"] = app_status(base_url, enabled, db_name)
            save(run_path / "manifest.json", manifest)
        manifest["statusAfterMeasure"] = app_status(base_url, enabled, db_name)
        manifest["capturedAt"] = timestamp()
        manifest["xviewSampleCount"] = collect_xview(base_url, run_path, run_id, enabled, manifest["capturedAt"])
        manifest["status"] = "VALID"
        manifest["finishedAt"] = timestamp()
        save(run_path / "manifest.json", manifest)
        validated = json.loads((run_path / "measure" / "validation.json").read_text(encoding="utf-8"))
        return validated
    except Exception as exc:
        manifest["invalidReasons"].append(f"{type(exc).__name__}: {exc}")
        manifest["status"] = "INVALID"
        manifest["finishedAt"] = timestamp()
        save(run_path / "manifest.json", manifest)
        print(f"INVALID {run_id}: {exc}", file=sys.stderr)
        return {"runId": run_id, "phase": "measure", "validity": "INVALID",
                "apmEnabled": enabled, "reasons": manifest["invalidReasons"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="print intended schedule without DB or Locust actions")
    parser.add_argument("--max-attempts-per-slot", type=int, default=2,
                        help="number of attempts before aborting invalid schedule (default: 2)")
    args = parser.parse_args()
    if args.max_attempts_per_slot < 1:
        parser.error("--max-attempts-per-slot must be >= 1")

    if args.dry_run:
        print("preliminary:", ["ON" if value else "OFF" for value in ORDER])
        print("official:   ", ["ON" if value else "OFF" for value in ORDER])
        print("each of 12 valid runs: NEW database + ONE server + 60s warmup Locust session + 60s measurement Locust session")
        print("warmup and measurement share database/server; each session ramps 0 -> 100 @ 10 users/s")
        print("preliminary and warmup: 601 coupons/user; official: ceil(2*M) coupons/user")
        print("validation: HTTP 201 per-request CSV <-> committed DB rows; INVALID runs retained and retried")
        return 0

    try:
        from dotenv import load_dotenv
        load_dotenv(ROOT / ".env")
        import requests, psycopg, locust, matplotlib
    except ImportError as exc:
        print(f"Missing Python dependencies: {exc}; install: pip install -r requirements.txt", file=sys.stderr)
        return 2
    for binary in ("docker", "locust"):
        if shutil.which(binary) is None:
            print(f"Missing executable: {binary}", file=sys.stderr)
            return 2
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    tag = datetime.now(timezone.utc).strftime("%m%d%H%M") + uuid.uuid4().hex[:6]
    url = f"http://127.0.0.1:{os.environ.get('APP_PORT', '8080')}"
    commit = process_output(["git", "rev-parse", "HEAD"])
    argv, env = compose(["up", "-d", "db"])
    command(argv, env)
    argv, env = compose(["build", "app"])
    command(argv, env)
    image_id = process_output(["docker", "image", "inspect", "--format", "{{.Id}}", "coupon-apm-lab:local"])
    results: list[dict] = []
    measured_prelim: list[dict] = []
    max_attempts = args.max_attempts_per_slot
    try:
        for family in ("pre", "offi"):
            measure_count = PRELIM_COUPONS_PER_USER if family == "pre" else max(1, 2 * max(
                int(v["maxUserSuccess"]) for v in measured_prelim))
            if family == "offi":
                print(f"PRELIM global maximum M={max(int(v['maxUserSuccess']) for v in measured_prelim)}; official coupons/user={measure_count}")
            for slot, enabled in enumerate(ORDER, 1):
                for attempt in range(max_attempts):
                    validation = run_one(tag, family, slot, enabled, measure_count, image_id, commit, url, attempt)
                    results.append(validation)
                    if validation["validity"] == "VALID":
                        if family == "pre":
                            measured_prelim.append(validation)
                        break
                else:
                    raise RuntimeError(f"{family} slot={slot} mode={enabled} had no VALID run after {max_attempts} attempts")
        from scripts.analysis import compare_off_on, write_comparison
        official_valid = [r for r in results if "_offi_" in r["runId"] and r["validity"] == "VALID"]
        comparison = compare_off_on(official_valid)
        comparison.update({"prelimRunIds": [v["runId"] for v in measured_prelim],
                           "M": max(int(v["maxUserSuccess"]) for v in measured_prelim),
                           "perUserCoupons": measure_count, "gitCommit": commit, "createdAt": timestamp()})
        save(EVIDENCE / f"batch-{tag}-comparison.json", comparison)
        write_comparison(EVIDENCE / "comparison.csv", official_valid, comparison)
        print("AC07 comparison:", json.dumps(comparison, ensure_ascii=False, indent=2))
        return 0 if comparison["target"] == "PASS" else 1
    finally:
        save(EVIDENCE / f"batch-{tag}-all-runs.json", results)


if __name__ == "__main__":
    raise SystemExit(main())
