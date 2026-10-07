#!/bin/sh
set -eu
env_file=${1:?Pass production Compose env file}
infra_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
docker compose --env-file "$env_file" -f "$infra_dir/docker-compose.prod.yml" exec -T proxy nginx -t
docker compose --env-file "$env_file" -f "$infra_dir/docker-compose.prod.yml" exec -T proxy nginx -s reload
