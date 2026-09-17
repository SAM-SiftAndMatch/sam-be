#!/usr/bin/env bash
set -euo pipefail

echo "==> Setting up pre-commit for this repository"

if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ python3 is required to install pre-commit."
  exit 1
fi

if command -v pipx >/dev/null 2>&1; then
  echo "==> Installing pre-commit via pipx"
  pipx install pre-commit --force
else
  echo "==> pipx not found. Installing pre-commit via pip --user"
  python3 -m pip install --user -U pre-commit

  if ! echo "${PATH:-}" | grep -q "$HOME/.local/bin"; then
    export PATH="$HOME/.local/bin:$PATH"
  fi
fi

if ! command -v pre-commit >/dev/null 2>&1; then
  echo "❌ pre-commit not found on PATH."
  echo "Please ensure ~/.local/bin is in PATH."
  exit 1
fi

echo "==> Installing git hooks"
pre-commit install
pre-commit install --hook-type commit-msg

echo "pre-commit is ready."
echo "Try:"
echo "  git commit -m \"chore: test precommit\""
