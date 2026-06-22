#!/usr/bin/env bash
#
# One-command deploy of CampusCoffee (prod profile) to Google Cloud Run.
#
# Usage:
#   scripts/deploy-cloudrun.sh                          # event-sourcing mode (default)
#   scripts/deploy-cloudrun.sh relational               # relational mode
#   scripts/deploy-cloudrun.sh --project my-gcp-project # target a project other than the active config
#   scripts/deploy-cloudrun.sh -y                        # skip the confirmation prompt (non-interactive)
#
# The target project comes from --project, or otherwise the active gcloud config
# (`gcloud config get-value project`). The script prints the resolved project, region, and mode and asks
# for confirmation before deploying, so a stale `gcloud config` cannot send the deploy to the wrong project
# unnoticed. It never hardcodes a project, so it stays usable for any account.
#
# One-time prerequisites:
#   gcloud auth login
#   gcloud config set project <your-project-id>   # or pass --project on each run
#   gcloud config set run/region <region>         # e.g. europe-west3
#   gcloud components install beta
#
# How it works: `gcloud run compose up` has no flag to set environment variables, so the JWT secret and the
# persistence mode are supplied through the Compose file's `env_file: deploy.env`. This script generates
# deploy.env with a random JWT secret on first run and reuses that secret afterwards (so JWTs issued earlier
# survive a redeploy), rewriting only the mode so the argument above always takes effect. deploy.env is
# gitignored and kept out of the Docker build context. `--allow-unauthenticated` grants public invocation in
# the same command, so the deploy is a single step; app-level authentication still gates write requests.

set -euo pipefail

MODE="event-sourcing"
PROJECT_OVERRIDE=""
ASSUME_YES=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    relational|event-sourcing) MODE="$1"; shift ;;
    --project) PROJECT_OVERRIDE="${2:-}"; [[ -n "$PROJECT_OVERRIDE" ]] || { echo "error: --project requires a project id" >&2; exit 2; }; shift 2 ;;
    --project=*) PROJECT_OVERRIDE="${1#--project=}"; shift ;;
    -y|--yes) ASSUME_YES=1; shift ;;
    *) echo "error: unexpected argument '$1' (expected 'relational' | 'event-sourcing' | --project <id> | -y)" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")/.."

# Resolve the target project: the explicit override wins, otherwise the active gcloud config.
if [[ -n "$PROJECT_OVERRIDE" ]]; then
  PROJECT="$PROJECT_OVERRIDE"
else
  PROJECT="$(gcloud config get-value project 2>/dev/null || true)"
fi
if [[ -z "$PROJECT" || "$PROJECT" == "(unset)" ]]; then
  echo "error: no target project. Pass --project <id> or run 'gcloud config set project <id>'." >&2
  exit 2
fi
REGION="$(gcloud config get-value run/region 2>/dev/null || true)"
[[ "$REGION" == "(unset)" ]] && REGION=""

# Show the resolved target and confirm, so a stale gcloud config cannot deploy to the wrong project.
echo "About to deploy to Google Cloud Run:"
echo "  service:          campus-coffee-prod"
echo "  project:          $PROJECT"
echo "  region:           ${REGION:-<unset; gcloud will prompt>}"
echo "  persistence mode: $MODE"
if [[ "$ASSUME_YES" -ne 1 ]]; then
  read -r -p "Continue? [y/N] " reply || reply=""
  [[ "$reply" =~ ^[Yy]([Ee][Ss])?$ ]] || { echo "Aborted."; exit 1; }
fi

# Generate the random JWT secret once; reuse it on later runs, but always (re)write the chosen mode.
if [[ -f deploy.env ]]; then
  secret="$(grep '^JWT_SECRET=' deploy.env | cut -d= -f2-)"
else
  secret="$(openssl rand -hex 32)"
  echo "Generated deploy.env (gitignored) with a random JWT secret."
fi
printf 'JWT_SECRET=%s\nCAMPUS_COFFEE_PERSISTENCE_MODE=%s\n' "$secret" "$MODE" > deploy.env

# Pass --project explicitly so every call targets exactly the project confirmed above.
echo "Deploying campus-coffee-prod to Cloud Run (persistence mode: $MODE)..."
gcloud beta run compose up compose.prod.yaml --project "$PROJECT" --allow-unauthenticated

echo "Service URL:"
gcloud run services describe campus-coffee-prod --project "$PROJECT" --format='value(status.url)'
