#!/usr/bin/env bash
set -e

COMMIT_MSG_FILE=$1

if [ ! -f "$COMMIT_MSG_FILE" ]; then
    echo "❌ Error: Commit message file not found: $COMMIT_MSG_FILE"
    exit 1
fi

COMMIT_MSG=$(head -n 1 "$COMMIT_MSG_FILE")

# Regex pattern for Conventional Commits
PATTERN="^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-zA-Z0-9_\.\-]+\))?!?: .{1,100}$"

if [[ ! "$COMMIT_MSG" =~ $PATTERN ]]; then
    echo "❌ [Invalid Commit Message] '$COMMIT_MSG'"
    echo "💡 Commit message must follow Conventional Commits specification:"
    echo "   <type>(<optional-scope>): <description>"
    echo ""
    echo "   Allowed types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert"
    echo "   Example: feat(auth): implement jwt authentication"
    exit 1
fi
