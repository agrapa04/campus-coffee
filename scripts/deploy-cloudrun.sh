#!/usr/bin/env bash
#
# One-command deploy of CampusCoffee (prod profile) to Google Cloud Run.
#
# Usage:
#   scripts/deploy-cloudrun.sh                  # event-sourcing mode (default)
#   scripts/deploy-cloudrun.sh relational       # relational mode
#
# One-time prerequisites:
#   gcloud auth login
#   gcloud config set project <your-project-id>
#   gcloud config set run/region <region>       # e.g. europe-west3
#   gcloud components install beta
#
# How it works: `gcloud run compose up` has no flag to set environment variables, so the JWT secret and the
# persistence mode are supplied through the Compose file's `env_file: deploy.env`. This script generates
# deploy.env with a random JWT secret on first run and reuses that secret afterwards (so JWTs issued earlier
# survive a redeploy), rewriting only the mode so the argument above always takes effect. deploy.env is
# gitignored and kept out of the Docker build context. `--allow-unauthenticated` grants public invocation in
# the same command, so the deploy is a single step; app-level authentication still gates write requests.

set -euo pipefail

MODE="${1:-event-sourcing}"
if [[ "$MODE" != "relational" && "$MODE" != "event-sourcing" ]]; then
  echo "error: persistence mode must be 'relational' or 'event-sourcing', got '$MODE'" >&2
  exit 2
fi

cd "$(dirname "$0")/.."

# Generate the random JWT secret once; reuse it on later runs, but always (re)write the chosen mode.
if [[ -f deploy.env ]]; then
  secret="$(grep '^JWT_SECRET=' deploy.env | cut -d= -f2-)"
else
  secret="$(openssl rand -hex 32)"
  echo "Generated deploy.env (gitignored) with a random JWT secret."
fi
printf 'JWT_SECRET=%s\nCAMPUS_COFFEE_PERSISTENCE_MODE=%s\n' "$secret" "$MODE" > deploy.env

echo "Deploying campus-coffee-prod to Cloud Run (persistence mode: $MODE)..."
gcloud beta run compose up compose.prod.yaml --allow-unauthenticated

echo "Service URL:"
gcloud run services describe campus-coffee-prod --format='value(status.url)'
