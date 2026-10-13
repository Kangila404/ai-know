#!/bin/sh
# Source this file. Only Docker is elevated; file transfers and backups stay owned by the deploy user.
aiknow_docker() {
    case "${AIKNOW_DOCKER_SUDO:-false}" in
        true) sudo -n /usr/bin/docker "$@" ;;
        false) docker "$@" ;;
        *) echo 'AIKNOW_DOCKER_SUDO must be true or false.' >&2; return 2 ;;
    esac
}

aiknow_compose() {
    if [ -n "${AIKNOW_RELEASE_ENV_FILE:-}" ]; then
        set -- --env-file "$AIKNOW_RELEASE_ENV_FILE" -f "${AIKNOW_COMPOSE_FILE:-$infra_dir/docker-compose.yml}" "$@"
    else
        set -- -f "${AIKNOW_COMPOSE_FILE:-$infra_dir/docker-compose.yml}" "$@"
    fi
    aiknow_docker compose --env-file "${AIKNOW_INFRA_ENV_FILE:-$infra_dir/.env}" "$@"
}
