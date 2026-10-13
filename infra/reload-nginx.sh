#!/bin/sh
set -eu
env_file=${1:?Pass production Compose env file}
infra_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$infra_dir/docker-command.sh"
export AIKNOW_INFRA_ENV_FILE="$env_file" AIKNOW_COMPOSE_FILE="$infra_dir/docker-compose.prod.yml"
aiknow_compose exec -T proxy nginx -t
aiknow_compose exec -T proxy nginx -s reload
