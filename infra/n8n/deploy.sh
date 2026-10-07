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
    running=$(compose ps --status running -q n8n)
    if [ -n "$running" ]; then
        restart_n8n=true
        compose stop -t 60 n8n
    fi
fi
if [ "$#" -ge 3 ]; then
    compose run --rm --no-deps n8n-workflows "$action" "$workflow" "$3"
else
    compose run --rm --no-deps n8n-workflows "$action" "$workflow"
fi
