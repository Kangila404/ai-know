#!/bin/sh
set -eu
attempt=0
until wget -q -O /dev/null http://127.0.0.1:8081/ready; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 30 ] || { echo 'Nginx did not enable HTTPS after certificate issuance.' >&2; exit 1; }
    sleep 2
done
