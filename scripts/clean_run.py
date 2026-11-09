#!/usr/bin/env python3
"""Explicitly drop ONE owned benchmark database; requires exact confirmation token."""
import argparse
import json
from pathlib import Path

# Direct CLI invocation also needs the repository root on sys.path.
import sys
if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--confirm", required=True, help="exact DROP:<runId>:<databaseName> token")
    args = parser.parse_args()
    from dotenv import load_dotenv
    load_dotenv(ROOT / ".env")
    from psycopg import sql
    from scripts.db import connect_db, database_name

    database = database_name(args.run_id)
    folder = ROOT / "docs/evidence/overhead" / args.run_id
    manifest_path = folder / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("runId") != args.run_id or manifest.get("database") != database:
        raise SystemExit("BLOCKED: runId/database mismatch with persisted Manifest")
    token = f"DROP:{args.run_id}:{database}"
    if args.confirm != token:
        raise SystemExit(f"BLOCKED: destructive action requires exact --confirm '{token}'")
    with connect_db("postgres") as admin:
        admin.autocommit = True
        with admin.cursor() as cur:
            cur.execute("SELECT shobj_description(oid, 'pg_database') FROM pg_database WHERE datname=%s", (database,))
            row = cur.fetchone()
            if row is None or row[0] != f"coupon-apm-lab runId:{args.run_id}":
                raise SystemExit("BLOCKED: DB has no expected run ownership comment")
            cur.execute("SELECT count(*) FROM pg_stat_activity WHERE datname = %s", (database,))
            if cur.fetchone()[0]:
                raise SystemExit("BLOCKED: target database still has active connections; stop its application first")
            cur.execute(sql.SQL("DROP DATABASE {}").format(sql.Identifier(database)))
    (folder / "cleaned.json").write_text(json.dumps({"runId": args.run_id, "database": database,
                        "action": "EXPLICIT_DROP", "manifestRetained": True}, indent=2) + "\n", encoding="utf-8")
    print(f"Dropped owned DB {database}; evidence retained at {folder}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
