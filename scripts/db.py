"""Run-owned PostgreSQL database lifecycle. Never drops a database implicitly."""
from __future__ import annotations

import os
import re
from pathlib import Path
from psycopg import connect, sql

RUN_ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]{6,48}$")


def require_run_id(run_id: str) -> str:
    if not RUN_ID_PATTERN.fullmatch(run_id):
        raise ValueError("invalid runId: use lowercase [a-z0-9_], 7-49 characters, starting with a letter")
    return run_id


def database_name(run_id: str) -> str:
    return "coupon_" + require_run_id(run_id)


def connect_db(database: str):
    return connect(host=os.environ.get("DB_HOST", "127.0.0.1"),
                   port=int(os.environ.get("DB_PORT", 5432)),
                   user=os.environ.get("DB_USER", "lab"),
                   password=os.environ.get("DB_PASSWORD", "local_development_only"),
                   dbname=database)


def create_run_db(run_id: str):
    name = database_name(run_id)
    with connect_db("postgres") as admin:
        admin.autocommit = True
        with admin.cursor() as cursor:
            cursor.execute("SELECT 1 FROM pg_database WHERE datname = %s", (name,))
            if cursor.fetchone():
                raise ValueError(f"database already exists (refusing to reuse): {name}")
            cursor.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(name)))
            cursor.execute(sql.SQL("COMMENT ON DATABASE {} IS {}").format(
                sql.Identifier(name), sql.Literal(f"coupon-apm-lab runId:{run_id}")))
    return name


def verify_database_schema(connection):
    with connection.cursor() as cur:
        cur.execute("SELECT current_database(), to_regclass('public.coupons'), to_regclass('public.coupon_issues')")
        current_db, coupons, issues = cur.fetchone()
        if not coupons or not issues:
            raise RuntimeError("Flyway schema absent; application must migrate this database first")
        cur.execute("SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1")
        row = cur.fetchone()
        if not row:
            raise RuntimeError("no successful Flyway migration")
        return {"database": current_db, "ddlVersion": row[0]}


def seed_phase(connection, run_id: str, phase: str, per_user: int, users: int = 100) -> dict:
    require_run_id(run_id)
    if phase not in {"warmup", "measure"}:
        raise ValueError("phase must be warmup or measure")
    if per_user < 1 or users < 1:
        raise ValueError("seed quantities must be positive")
    prefix = f"{run_id}.{phase}."
    expected = per_user * users
    with connection.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM coupons WHERE name LIKE %s", (prefix + "%",))
        if cur.fetchone()[0]:
            raise RuntimeError(f"phase already seeded: {prefix}")
        # SQL generates ids through the declared versioned sequence; no per-row Python INSERT calls.
        cur.execute("""
            INSERT INTO coupons (id, name, total_stock, issued_count, created_at)
            SELECT nextval('coupon_seq'), %s || n::text, 1, 0, NOW()
              FROM generate_series(0, %s) AS g(n)
            RETURNING id, name
        """, (prefix, expected - 1))
        rows = cur.fetchall()
    if len(rows) != expected:
        connection.rollback()
        raise RuntimeError("unexpected number of seeded coupon rows")
    mapped = [None] * expected
    for coupon_id, name in rows:
        index = int(name.removeprefix(prefix))
        if not (0 <= index < expected) or mapped[index] is not None:
            connection.rollback()
            raise RuntimeError(f"unexpected returned coupon index: {name}")
        mapped[index] = int(coupon_id)
    if any(value is None for value in mapped):
        connection.rollback()
        raise RuntimeError("coupon id allocation gaps")
    connection.commit()
    allocations = []
    for idx in range(users):
        allocations.append({
            "virtualUserIndex": idx,
            "userId": f"{run_id}_{phase}_u{idx:03d}",
            "couponIds": mapped[idx * per_user:(idx + 1) * per_user],
        })
    with connection.cursor() as cur:
        cur.execute("""
            SELECT COUNT(*), COALESCE(SUM(issued_count), 0), MIN(total_stock), MAX(total_stock)
              FROM coupons WHERE name LIKE %s
        """, (prefix + "%",))
        count, issued, low_stock, high_stock = cur.fetchone()
        if count != expected or issued != 0 or low_stock != 1 or high_stock != 1:
            raise RuntimeError("post-seed DB state differs from expected stock=1/issued=0")
    return {"runId": run_id, "phase": phase, "users": allocations,
            "couponsPerUser": per_user, "seededCoupons": expected}


def issued_pairs(connection, run_id: str, phase: str) -> list[tuple[int, str]]:
    require_run_id(run_id)
    if phase not in {"warmup", "measure"}:
        raise ValueError("invalid phase")
    with connection.cursor() as cur:
        cur.execute("SELECT coupon_id, user_id FROM coupon_issues WHERE user_id LIKE %s",
                    (f"{run_id}_{phase}_u%",))
        return cur.fetchall()
