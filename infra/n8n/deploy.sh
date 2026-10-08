#!/bin/sh
set -eu
infra_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
action=${1:-check}
workflow=${2:-ai-know-news-draft.json}
[ "$#" -le 3 ] || { echo 'Too many arguments.' >&2; exit 2; }
case "$action" in check|apply) ;; *) echo 'Usage: sh deploy.sh check|apply [filename.json] [--replace]' >&2; exit 2 ;; esac
compose() { docker compose --env-file "${AIKNOW_INFRA_ENV_FILE:-$infra_dir/.env}" -f "${AIKNOW_COMPOSE_FILE:-$infra_dir/docker-compose.yml}" "$@"; }
restart_n8n=false
cleanup() {
    result=$?
    trap - EXIT
    if [ "$restart_n8n" = true ]; then compose start n8n || result=1; fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT TERM
if [ "$action" = apply ]; then
    # Read the effective token from Spring, not a second copy in infra.env.
    # Capture it silently and pass it via the process environment (never CLI args).
    services=$(compose config --services)
    for service in $services; do
        if [ "$service" = server ]; then
            N8N_INGEST_TOKEN=$(compose exec -T server printenv N8N_INGEST_TOKEN) || {
                echo 'Cannot read ingestion token. Start/recreate the server with its updated env file first.' >&2
                exit 1
            }
            [ -n "$N8N_INGEST_TOKEN" ] || { echo 'Server ingestion token is empty.' >&2; exit 1; }
            export N8N_INGEST_TOKEN
        fi
    done
    running=$(compose ps --status running -q n8n)
    if [ -n "$running" ]; then
        restart_n8n=true
        compose stop -t 60 n8n
    fi
fi
run_tool() {
    if [ -n "${N8N_INGEST_TOKEN:-}" ]; then
        compose run --rm --no-deps -e N8N_INGEST_TOKEN n8n-workflows "$@"
    else
        compose run --rm --no-deps n8n-workflows "$@"
    fi
}
if [ "$#" -ge 3 ]; then
    run_tool "$action" "$workflow" "$3"
else
    run_tool "$action" "$workflow"
fi
