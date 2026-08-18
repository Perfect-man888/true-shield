#!/usr/bin/env sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_ROOT"

if [ ! -f .env.production ]; then
    echo "Missing .env.production. Copy deploy/production.env.example and replace every placeholder." >&2
    exit 1
fi

if grep -q 'replace_with_' .env.production; then
    echo "Unsafe placeholder remains in .env.production; deployment stopped." >&2
    exit 1
fi

docker compose \
    --env-file .env.production \
    -f deploy/compose.production.yml \
    config >/dev/null

docker compose \
    --env-file .env.production \
    -f deploy/compose.production.yml \
    up -d --build

docker compose \
    --env-file .env.production \
    -f deploy/compose.production.yml \
    ps

docker compose \
    --env-file .env.production \
    -f deploy/compose.production.yml \
    exec -T api python -c \
    "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/api/v1/health', timeout=5).read().decode())"
