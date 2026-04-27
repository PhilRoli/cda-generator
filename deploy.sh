#!/usr/bin/env bash
set -euo pipefail

REMOTE="${DEPLOY_REMOTE:-}"
REMOTE_DIR="${DEPLOY_DIR:-/opt/apps/cda-uebung}"

if [[ -z "$REMOTE" ]]; then
  echo "DEPLOY_REMOTE ist nicht gesetzt (z.B. deploy@203.0.113.10)." >&2
  echo "Beispiel: DEPLOY_REMOTE='deploy@203.0.113.10' ./deploy.sh" >&2
  exit 1
fi

echo "→ Building dist/"
./scripts/build-dist.sh

echo "→ Syncing files to server"
rsync -az --delete \
  --exclude='.git/' \
  --exclude='.env' \
  --exclude='.DS_Store' \
  --exclude='target/' \
  --exclude='data/' \
  --exclude='elga-lib/' \
  . "$REMOTE:$REMOTE_DIR"

echo "→ Rebuilding backend container"
ssh "$REMOTE" "cd $REMOTE_DIR && docker compose up -d --build"

echo "✓ Deploy sync complete"
