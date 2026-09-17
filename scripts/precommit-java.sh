#!/usr/bin/env bash
set -euo pipefail

GREEN="\033[0;32m"
YELLOW="\033[0;33m"
BLUE="\033[0;34m"
GRAY="\033[0;90m"
NC="\033[0m"

step() {
  echo -e "${BLUE}▶ $1${NC}"
}

ok() {
  echo -e "  ${GREEN}✔ $1${NC}"
}

echo -e "${YELLOW}☕ Java pre-commit${NC}"

CHANGED=$(git diff --cached --name-only)
if ! echo "$CHANGED" | grep -E '\.java$|pom\.xml$' >/dev/null 2>&1; then
  echo -e "${GRAY}No Java-related changes staged. Skipping.${NC}"
  exit 0
fi

step "Reminder"
echo -e "${YELLOW}⚠ Please run ${BLUE}make fmt${NC}${YELLOW} before pushing.${NC}"
echo -e "${GRAY}Formatting & style are enforced in CI.${NC}"
ok "Reminder shown"

exit 0
