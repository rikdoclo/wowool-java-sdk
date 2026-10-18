#!/usr/bin/env bash
# ==============================================================================
# local-verify.sh — Local Verification and Quality Gate Harness
# Wowool Java SDK (c) Rik Doclo
# ==============================================================================
# Usage:
#   ./scripts/local-verify.sh          # Default full verification
#   ./scripts/local-verify.sh --fast   # Compilation and tests only
#   ./scripts/local-verify.sh --format # Apply Spotless formatting first
#   ./scripts/local-verify.sh --full   # Full check (Spotless + Checkstyle + Tests)
# ==============================================================================

set -euo pipefail

MODE="full"
if [ "${1:-}" = "--fast" ]; then
    MODE="fast"
elif [ "${1:-}" = "--format" ]; then
    MODE="format"
elif [ "${1:-}" = "--full" ]; then
    MODE="full"
fi

echo "================================================================================"
echo "▶ Running Wowool Java SDK Verification Harness [Mode: $MODE]"
echo "================================================================================"

# Step 1: Optional formatting
if [ "$MODE" = "format" ]; then
    echo "▶ [1/4] Applying code formatting with Spotless..."
    mvn spotless:apply
fi

# Step 2: Compilation
echo "▶ [2/4] Compiling Java source and test code..."
mvn test-compile

# Step 3: Static code analysis
if [ "$MODE" = "full" ] || [ "$MODE" = "format" ]; then
    echo "▶ [3/4] Running quality gates (Spotless & Checkstyle)..."
    echo "  → Spotless check..."
    mvn spotless:check
    echo "  → Checkstyle check (0 violations policy)..."
    mvn checkstyle:check
else
    echo "▶ [3/4] Skipping style checks in fast mode (--fast)."
fi

# Step 4: Test suite & benchmarks
echo "▶ [4/4] Running automated test suite & regression checks..."
mvn test

echo "================================================================================"
echo "✔ Success: All verification gates passed! Code is ready for commit or release."
echo "================================================================================"
exit 0
