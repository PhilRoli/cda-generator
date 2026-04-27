#!/usr/bin/env bash
set -euo pipefail

REMOTE="deploy@46.225.72.0"
REMOTE_DIR="/opt/apps/cda-uebung"

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
