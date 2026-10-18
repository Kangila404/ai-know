#!/bin/sh
set -eu
# The workflow streams the Docker helper before this script, before any release exists on the host.
if ! command -v aiknow_docker >/dev/null 2>&1; then
    . "$(dirname -- "$0")/docker-command.sh"
fi
fail() { printf 'Deployment preflight failed: %s\n' "$1" >&2; exit 1; }
echo 'SSH login succeeded. Checking deployment files and Docker access...'
[ -d /opt/aiknow ] || fail '/opt/aiknow is missing or inaccessible. Prepare the persistent deployment path.'
[ -w /opt/aiknow ] || fail 'The deployment account cannot write to /opt/aiknow.'
for file in /opt/aiknow/infra.env /opt/aiknow/server.env; do
    [ -f "$file" ] || fail "$file is missing or is not a regular file."
    [ -r "$file" ] || fail "$file is not readable by the deployment account."
    [ -s "$file" ] || fail "$file is empty."
done
aiknow_docker info >/dev/null || fail 'Docker is unavailable. For CD, allow passwordless sudo for /usr/bin/docker and check the daemon.'
aiknow_docker compose version >/dev/null || fail 'The Docker Compose plugin is unavailable.'
echo 'Deployment prerequisites passed.'
