# Quotient — developer entrypoint. Thin wrappers over Gradle and Docker Compose
# so the common workflows are one word. `make` with no target prints help.
#
# Some targets (seed/demo/bench/ledger-verify) are filled in as their owning
# phase lands; they print a clear "not yet implemented" notice until then.

SHELL := /bin/bash
COMPOSE := docker compose
GRADLE := ./gradlew

.DEFAULT_GOAL := help

## ---------------------------------------------------------------------------
## Infrastructure
## ---------------------------------------------------------------------------

.PHONY: up
up: ## Start local infrastructure (Kafka, Postgres, Redis, observability, Keycloak)
	$(COMPOSE) up -d
	@echo ""
	@echo "Infra starting. Endpoints:"
	@echo "  Kafka (host)     localhost:29092      Kafka UI      http://localhost:8085"
	@echo "  Postgres         localhost:5432       Redis         localhost:6379"
	@echo "  Prometheus       http://localhost:9090  Grafana     http://localhost:3001 (admin/admin)"
	@echo "  Tempo            http://localhost:3200  Keycloak    http://localhost:8081 (admin/admin)"
	@echo "  OTLP             grpc://localhost:4317  Toxiproxy   http://localhost:8474"

.PHONY: down
down: ## Stop infrastructure (keep volumes)
	$(COMPOSE) down

.PHONY: down-v
down-v: ## Stop infrastructure and delete volumes (clean slate)
	$(COMPOSE) down -v

.PHONY: ps
ps: ## Show infrastructure status
	$(COMPOSE) ps

.PHONY: logs
logs: ## Tail infrastructure logs
	$(COMPOSE) logs -f --tail=100

## ---------------------------------------------------------------------------
## Build / test / quality
## ---------------------------------------------------------------------------

.PHONY: build
build: ## Compile all modules and assemble jars
	$(GRADLE) build -x integrationTest

.PHONY: test
test: ## Run unit tests
	$(GRADLE) test

.PHONY: itest
itest: ## Run integration tests (Testcontainers)
	$(GRADLE) integrationTest

.PHONY: coverage
coverage: ## Build the aggregate JaCoCo report and open it
	$(GRADLE) coverage
	@echo "Report: build/reports/jacoco/testCodeCoverageReport/html/index.html"
	@command -v open >/dev/null && open build/reports/jacoco/testCodeCoverageReport/html/index.html || true

.PHONY: lint
lint: ## Run Spotless + Checkstyle
	$(GRADLE) spotlessCheck checkstyleMain checkstyleTest

.PHONY: format
format: ## Auto-format with Spotless
	$(GRADLE) spotlessApply

## ---------------------------------------------------------------------------
## Data / demo / benchmarks  (filled in by later phases)
## ---------------------------------------------------------------------------

.PHONY: seed
seed: ## Seed 3 demo tenants + plans + meters
	@bash scripts/seed.sh 2>/dev/null || echo "[seed] not yet implemented (Phase 3)."

.PHONY: demo
demo: ## End-to-end demo: fire traffic, generate invoices, print results
	@bash scripts/demo.sh 2>/dev/null || echo "[demo] not yet implemented (Phase 7)."

.PHONY: bench
bench: ## Run the WebFlux-vs-vthreads benchmark suite
	@bash loadtest/run.sh 2>/dev/null || echo "[bench] not yet implemented (Phase 10)."

.PHONY: ledger-verify
ledger-verify: ## Re-compute all balances from entries and assert they match
	@$(GRADLE) :ledger-service:ledgerVerify 2>/dev/null || echo "[ledger-verify] not yet implemented (Phase 3)."

## ---------------------------------------------------------------------------
## Help
## ---------------------------------------------------------------------------

.PHONY: help
help: ## Show this help
	@echo "Quotient — make targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
