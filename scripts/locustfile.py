"""Locust single-process request recording: bounded queue -> dedicated CSV writer.

The writer is not on the synchronous HTTP completion path. Any loss marks the run invalid.
"""
import csv
import json
import os
import queue
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

from locust import HttpUser, between, events, task
from locust.exception import StopUser

CSV_FIELDS = ["runId", "phase", "virtualUserIndex", "userId", "couponId",
              "startedAt", "completedAt", "httpStatus", "responseTimeMs", "success", "error"]


def iso_now():
    return datetime.now(timezone.utc).isoformat()


class Recorder:
    def __init__(self, output: Path, run_id: str, phase: str):
        self.output = output
        self.run_id = run_id
        self.phase = phase
        self.pending = queue.Queue(maxsize=20_000)
        self.errors = []
        self.written = 0
        self.lock = threading.Lock()
        self.thread = threading.Thread(target=self.write_loop, name="LocustCSVWriter", daemon=True)
        self.window_start = iso_now()
        self.window_start_epoch = time.time()
        self.user_starts = set()
        self.thread.start()

    def problem(self, reason):
        with self.lock:
            self.errors.append(str(reason))

    def write_loop(self):
        try:
            with (self.output / "requests.csv").open("w", newline="", encoding="utf-8") as file:
                writer = csv.DictWriter(file, fieldnames=CSV_FIELDS)
                writer.writeheader()
                while True:
                    row = self.pending.get()
                    try:
                        if row is None:
                            return
                        writer.writerow(row)
                        self.written += 1
                    finally:
                        self.pending.task_done()
        except Exception as exc:
            self.problem(f"CSV Writer failure: {exc}")

    def record(self, row):
        try:
            self.pending.put_nowait(row)
        except queue.Full:
            self.problem("CSV writer queue exhausted; request record lost")

    def close(self, environment):
        try:
            self.pending.put(None, timeout=3)
        except queue.Full:
            self.problem("CSV writer queue remains full at shutdown")
        self.thread.join(timeout=15)
        if self.thread.is_alive() or self.pending.unfinished_tasks:
            self.problem("writer did not flush all requests within 15s")
        # Locust's cumulative request count is checked against detailed request rows.
        locust_count = environment.stats.total.num_requests
        if locust_count != self.written:
            self.problem(f"Locust total {locust_count} != CSV {self.written}")
        if len(self.user_starts) != 100:
            self.problem(f"users started: expected 100, observed {len(self.user_starts)}")
        summary = {"runId": self.run_id, "phase": self.phase, "windowStart": self.window_start,
                   "windowEnd": datetime.fromtimestamp(self.window_start_epoch + 60, timezone.utc).isoformat(),
                   "recordsCount": self.written, "locustCount": locust_count,
                   "startedUsers": len(self.user_starts), "errors": self.errors}
        (self.output / "writer_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")


RECORDER = None
ALLOCATIONS = None
BUNDLES = None


@events.test_start.add_listener
def on_start(environment, **kwargs):
    global RECORDER, ALLOCATIONS, BUNDLES
    folder = Path(os.environ["EVIDENCE_PHASE_DIR"])
    folder.mkdir(parents=True, exist_ok=True)
    allocation = json.loads(Path(os.environ["LOCUST_ALLOCATION_FILE"]).read_text(encoding="utf-8"))
    RECORDER = Recorder(folder, os.environ["EXPERIMENT_RUN_ID"], os.environ["EXPERIMENT_PHASE"])
    ALLOCATIONS = allocation
    if allocation["runId"] != RECORDER.run_id or allocation["phase"] != RECORDER.phase:
        RECORDER.problem("allocation runId/phase mismatch")
        environment.runner.quit()
    BUNDLES = queue.Queue()
    for bundle in allocation["users"]:
        BUNDLES.put_nowait(bundle)


@events.request.add_listener
def on_request(request_type, name, response_time, response_length, response=None,
               context=None, exception=None, **kwargs):
    if RECORDER is None:
        return
    ctx = context or {}
    http_status = response.status_code if response is not None else 0
    ok = http_status == 201 and exception is None
    RECORDER.record({
        "runId": RECORDER.run_id, "phase": RECORDER.phase,
        "virtualUserIndex": ctx.get("virtualUserIndex", ""),
        "userId": ctx.get("userId", ""), "couponId": ctx.get("couponId", ""),
        "startedAt": ctx.get("startedAt", ""), "completedAt": iso_now(),
        "httpStatus": http_status, "responseTimeMs": round(float(response_time), 5),
        "success": str(ok).lower(), "error": str(exception or ("" if ok else f"HTTP {http_status}")),
    })


@events.quitting.add_listener
def on_quitting(environment, **kwargs):
    if RECORDER is not None:
        RECORDER.close(environment)
        if RECORDER.errors:
            environment.process_exit_code = 2


class IssuerUser(HttpUser):
    wait_time = between(0.1, 0.3)

    def on_start(self):
        try:
            self.bundle = BUNDLES.get_nowait()
            self.position = 0
            RECORDER.user_starts.add(self.bundle["virtualUserIndex"])
        except queue.Empty:
            RECORDER.problem("coupon allocation queue exhausted or duplicate Locust user")
            self.environment.runner.quit()
            raise StopUser()

    @task
    def issue(self):
        ids = self.bundle["couponIds"]
        if self.position >= len(ids):
            RECORDER.problem(f"coupon pool exhausted for user {self.bundle['virtualUserIndex']}")
            self.environment.runner.quit()
            raise StopUser()
        coupon_id = ids[self.position]
        self.position += 1
        ctx = {"virtualUserIndex": self.bundle["virtualUserIndex"],
               "userId": self.bundle["userId"], "couponId": coupon_id,
               "startedAt": iso_now()}
        with self.client.post(f"/api/coupons/{coupon_id}/issues",
                              name="POST /api/coupons/{couponId}/issues",
                              json={"userId": self.bundle["userId"]},
                              context=ctx, catch_response=True) as response:
            if response.status_code != 201:
                response.failure(f"unexpected HTTP {response.status_code}: {response.text[:200]}")
