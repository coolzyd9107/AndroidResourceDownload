GO ?= go
BIN_DIR := bin
SERVER_BIN := $(BIN_DIR)/server
SEED_BIN := $(BIN_DIR)/seed-admin

PKG := ./...
PKG_LIST := $(shell go list $(PKG))

.PHONY: help
help:
	@echo "Available targets:"
	@echo "  make build          - Build server binary"
	@echo "  make test           - Run unit tests"
	@echo "  make vet            - Run go vet"
	@echo "  make tidy           - go mod tidy"
	@echo "  make run-sqlite     - Run with SQLite (default local dev)"
	@echo "  make run-pg         - Run with PostgreSQL (DATABASE_DRIVER=postgres)"
	@echo "  make seed-admin ID=<github_id> LOGIN=<login> [NOTE=...] - Add GitHub admin whitelist entry"
	@echo "  make clean          - Remove build artifacts"

.PHONY: build
build:
	@mkdir -p $(BIN_DIR)
	$(GO) build -o $(SERVER_BIN) ./cmd/server

.PHONY: test
test:
	$(GO) test -race -count=1 $(PKG)

.PHONY: vet
vet:
	$(GO) vet $(PKG)

.PHONY: tidy
tidy:
	$(GO) mod tidy

.PHONY: run-sqlite
run-sqlite: build
	DATABASE_DRIVER=sqlite EMAIL_MODE=console ./$(SERVER_BIN)

.PHONY: run-pg
run-pg: build
	DATABASE_DRIVER=postgres ./$(SERVER_BIN)

.PHONY: seed-admin
seed-admin: build
	$(GO) run ./scripts ID=$(ID) LOGIN=$(LOGIN) NOTE="$(NOTE)"

.PHONY: clean
clean:
	rm -rf $(BIN_DIR)
