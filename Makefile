SHELL := /bin/sh
.PHONY: setup run test-unit test-integration test-lock-wait test-overhead test-python evidence clean-run

setup:
	@test -f .env || cp .env.example .env
	docker compose up -d db

run:
	docker compose up -d --build db app

# Unit: AC04/05/06 and buffer state races; no Docker required after dependencies resolve.
test-unit:
	./gradlew test

# Service/DB integration and APM ON/OFF HTTP business contract: Docker/Testcontainers required.
test-integration:
	./gradlew integrationTest --tests '*CouponIssueConcurrencyIT' --tests '*CouponIssueServiceIT' --tests '*Apm*CouponHttpIT'

# Real PostgreSQL blocked row and 3-second timeout: Docker/Testcontainers required.
test-lock-wait:
	./gradlew integrationTest --tests '*PostgresLockWaitHttpIT'

# AC07 full 12-run benchmark. Creates independent DBs and DOES NOT DELETE them.
test-overhead:
	python3 scripts/benchmark.py

test-python:
	python3 -m unittest discover -s tests -v

# Does not invent measurements. Requires existing actual lock-wait raw data + manifest.
evidence:
	python3 scripts/evidence.py

# Explicitly destructive; needs exact scope and confirmation string.
clean-run:
	@test -n "$(RUN_ID)" || (echo 'Set RUN_ID=<run identifier>'; exit 2)
	@test -n "$(CONFIRM)" || (echo 'Set CONFIRM=DROP:<runId>:coupon_<runId>'; exit 2)
	python3 scripts/clean_run.py --run-id '$(RUN_ID)' --confirm '$(CONFIRM)'
