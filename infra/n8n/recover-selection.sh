#!/bin/sh
set -eu
infra_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$infra_dir/docker-command.sh"
[ "$#" = 1 ] || { echo 'Usage: recover-selection.sh GENERATION_RECORD_ID' >&2; exit 2; }
case "$1" in ''|*[!0-9]*) echo 'Invalid record ID' >&2; exit 2;; esac
# Prefer the same env/Compose configuration as the release deployment.
AIKNOW_INFRA_ENV_FILE=${AIKNOW_INFRA_ENV_FILE:-/opt/aiknow/infra.env}
AIKNOW_COMPOSE_FILE=${AIKNOW_COMPOSE_FILE:-$infra_dir/docker-compose.prod.yml}
export AIKNOW_INFRA_ENV_FILE AIKNOW_COMPOSE_FILE
compose() { aiknow_compose "$@"; }
token=$(compose exec -T server printenv N8N_INGEST_TOKEN)
[ -n "$token" ] || { echo 'Missing server ingestion token' >&2; exit 1; }
restart_n8n=false
cleanup() {
    result=$?
    trap - EXIT
    if [ "$restart_n8n" = true ]; then compose start n8n || result=1; fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT TERM
if [ -n "$(compose ps --status running -q n8n)" ]; then
    restart_n8n=true
    compose stop -t 60 n8n
fi
printf '%s' "$token" | compose run --rm --no-deps -T --entrypoint node n8n-workflows /opt/aiknow/recover-selection-context.mjs "$1"
