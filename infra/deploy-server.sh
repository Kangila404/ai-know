#!/bin/sh
set -eu
env_file=${1:?Pass the absolute production Compose env file}
image=${2:?Pass the immutable server image}
case "$env_file" in /*) ;; *) echo 'Use an absolute env file path.' >&2; exit 2;; esac
if ! printf '%s\n' "$image" | LC_ALL=C grep -Eq '^docker\.io/[a-z0-9][a-z0-9_-]*/aiknow-server:[a-f0-9]{40}$'; then
    echo 'Use the released Docker Hub aiknow-server image with a full commit SHA tag.' >&2
    exit 2
fi
infra_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
state_dir=$(dirname -- "$env_file")
. "$infra_dir/docker-command.sh"
# sudo resets the environment. Pass the verified image through a second Compose env file.
umask 077
AIKNOW_RELEASE_ENV_FILE=$(mktemp "$state_dir/.release-env.XXXXXX")
stage=configuration
backup_tmp=
cleanup() {
    result=$?
    trap - EXIT
    rm -f -- "$AIKNOW_RELEASE_ENV_FILE"
    if [ -n "$backup_tmp" ]; then rm -f -- "$backup_tmp"; fi
    if [ "$result" -ne 0 ]; then
        printf 'Deployment failed during %s (exit %s). Data and certificate volumes were retained.\n' "$stage" "$result" >&2
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT TERM
begin_stage() { stage=$1; printf '[%s] Deployment stage: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$stage"; }
printf 'SERVER_IMAGE=%s\n' "$image" > "$AIKNOW_RELEASE_ENV_FILE"
export AIKNOW_RELEASE_ENV_FILE
export AIKNOW_INFRA_ENV_FILE="$env_file" AIKNOW_COMPOSE_FILE="$infra_dir/docker-compose.prod.yml"
export SERVER_IMAGE="$image"
compose() { aiknow_compose "$@"; }
compose config --quiet
previous=$(compose ps -q server)
if [ -n "$previous" ]; then
    aiknow_docker inspect --format '{{.Config.Image}}' "$previous" > "$state_dir/previous-server-image"
fi
# Validate account settings before changing running services or touching the database.
begin_stage image-download
compose pull server proxy n8n certbot mysql
begin_stage https-certificate
compose run --rm --no-deps certbot check
# The proxy can serve ACME HTTP challenges without an application or existing PEM files.
compose up -d --no-deps --wait --wait-timeout 90 proxy
# Avoid overlapping Certbot processes sharing its account/renewal lock.
compose stop certbot
if ! compose run --rm --no-deps certbot issue; then
    echo 'Certificate issuance failed. Check public DNS, inbound port 80, ACME settings and certbot output.' >&2
    compose up -d --no-deps certbot
    exit 1
fi
compose up -d --no-deps certbot
compose exec -T proxy sh /opt/aiknow/nginx/wait-https.sh
begin_stage mysql-readiness
if ! compose up -d --wait --wait-timeout 180 mysql; then
    echo 'MySQL is not ready for authenticated TCP queries. Check initialization logs and whether the existing database credentials match infra.env. Keep the database volume.' >&2
    exit 1
fi
umask 077
begin_stage database-backup
mkdir -p "$state_dir/backups"
backup="$state_dir/backups/mysql-$(date -u +%Y%m%dT%H%M%SZ).sql"
backup_tmp="$backup.tmp"
if ! compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump --protocol=TCP -h 127.0.0.1 -u root --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > "$backup_tmp"; then
    echo 'MySQL backup failed; application deployment stopped. Check database credentials and mysqldump output. Keep the database volume.' >&2
    exit 1
fi
if [ ! -s "$backup_tmp" ]; then
    echo 'MySQL backup is empty; application deployment stopped.' >&2
    exit 1
fi
mv "$backup_tmp" "$backup"
backup_tmp=
# The application applies Flyway then validates the schema. Existing DB adoption is manual.
# Failed migrations stop here: never delete data or automatically run migration repair/clean.
begin_stage application-readiness
if ! compose up -d --wait --wait-timeout 180 server proxy n8n; then
    echo "Deployment failed. Database backup: $backup. Review migration/schema compatibility before rolling back the image." >&2
    exit 1
fi
begin_stage n8n-workflow
AIKNOW_INFRA_ENV_FILE="$env_file" AIKNOW_COMPOSE_FILE="$infra_dir/docker-compose.prod.yml" sh "$infra_dir/n8n/deploy.sh" apply
printf '%s\n' "$image" > "$state_dir/current-server-image"
echo 'Server is healthy; workflow imported as an unpublished draft when changed.'
