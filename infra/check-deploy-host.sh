#!/bin/sh
set -eu
fail() { printf 'NAS preflight failed: %s\n' "$1" >&2; exit 1; }
echo 'SSH login succeeded. Checking deployment files and Docker access...'
[ -d /opt/aiknow ] || fail '/opt/aiknow is missing or inaccessible. Prepare the persistent deployment path.'
[ -w /opt/aiknow ] || fail 'The deployment account cannot write to /opt/aiknow.'
for file in /opt/aiknow/infra.env /opt/aiknow/server.env; do
    [ -f "$file" ] || fail "$file is missing or is not a regular file."
    [ -r "$file" ] || fail "$file is not readable by the deployment account."
    [ -s "$file" ] || fail "$file is empty."
done
command -v docker >/dev/null 2>&1 || fail 'docker is not available in the SSH session PATH.'
docker info >/dev/null || fail 'Docker is unavailable to the deployment account. Check daemon state and socket permissions.'
docker compose version >/dev/null || fail 'The Docker Compose plugin is unavailable.'
echo 'NAS deployment prerequisites passed.'
