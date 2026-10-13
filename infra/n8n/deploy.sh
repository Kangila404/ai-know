#!/bin/sh
set -eu
infra_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$infra_dir/docker-command.sh"
action=${1:-check}
workflow=${2:-ai-know-news-draft.json}
[ "$#" -le 3 ] || { echo 'Too many arguments.' >&2; exit 2; }
case "$action" in check|apply) ;; *) echo 'Usage: sh deploy.sh check|apply [filename.json] [--replace]' >&2; exit 2 ;; esac
compose() { aiknow_compose "$@"; }
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
    # Capture it silently; sudo deployments pass it through stdin, never CLI args or files.
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
        if [ "${AIKNOW_DOCKER_SUDO:-false}" = true ]; then
            printf '%s' "$N8N_INGEST_TOKEN" | compose run --rm --no-deps -T n8n-workflows "$@" --token-stdin
        else
            compose run --rm --no-deps -e N8N_INGEST_TOKEN n8n-workflows "$@"
        fi
    else
        compose run --rm --no-deps n8n-workflows "$@"
    fi
}
if [ "$#" -ge 3 ]; then
    run_tool "$action" "$workflow" "$3"
else
    run_tool "$action" "$workflow"
fi
