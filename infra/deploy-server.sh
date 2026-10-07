#!/bin/sh
set -eu
env_file=${1:?Pass the absolute production Compose env file}
image=${2:?Pass the immutable server image}
case "$env_file" in /*) ;; *) echo 'Use an absolute env file path.' >&2; exit 2;; esac
case "$image" in ghcr.io/*/server:*) ;; *) echo 'Use the released GHCR server image.' >&2; exit 2;; esac
infra_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
state_dir=$(dirname -- "$env_file")
export SERVER_IMAGE="$image"
compose() { docker compose --env-file "$env_file" -f "$infra_dir/docker-compose.prod.yml" "$@"; }
compose config --quiet
previous=$(compose ps -q server)
if [ -n "$previous" ]; then
    docker inspect --format '{{.Config.Image}}' "$previous" > "$state_dir/previous-server-image"
fi
compose pull server proxy n8n
compose up -d --wait --wait-timeout 180 mysql
umask 077
mkdir -p "$state_dir/backups"
backup="$state_dir/backups/mysql-$(date -u +%Y%m%dT%H%M%SZ).sql"
compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -u root --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > "$backup.tmp"
test -s "$backup.tmp"
mv "$backup.tmp" "$backup"
# The application applies Flyway then validates the schema. Existing DB adoption is manual.
# Failed migrations stop here: never delete data or automatically run migration repair/clean.
if ! compose up -d --wait --wait-timeout 180 server proxy n8n; then
    echo "Deployment failed. Database backup: $backup. Review migration/schema compatibility before rolling back the image." >&2
    exit 1
fi
AIKNOW_INFRA_ENV_FILE="$env_file" AIKNOW_COMPOSE_FILE="$infra_dir/docker-compose.prod.yml" sh "$infra_dir/n8n/deploy.sh" apply
printf '%s\n' "$image" > "$state_dir/current-server-image"
echo 'Server is healthy; workflow imported as an unpublished draft when changed.'
