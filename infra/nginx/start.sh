#!/bin/sh
set -eu
printf '%s\n' "${API_DOMAIN:-}" | LC_ALL=C grep -Eq '^([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$' || {
    echo 'API_DOMAIN must be a DNS hostname, without scheme, path or port.' >&2; exit 2;
}
export API_DOMAIN
cert_dir="/etc/letsencrypt/live/$API_DOMAIN"
config=/etc/nginx/conf.d/default.conf
fingerprint() {
    if [ -s "$cert_dir/fullchain.pem" ] && [ -s "$cert_dir/privkey.pem" ]; then
        sha256sum "$cert_dir/fullchain.pem" "$cert_dir/privkey.pem"
    else
        printf 'missing\n'
    fi
}
render() {
    template=default.conf.template
    [ "$1" != missing ] || template=bootstrap.conf.template
    envsubst '${API_DOMAIN}' < "/opt/aiknow/nginx/$template" > "$config"
}
current=$(fingerprint)
render "$current"
nginx -t
nginx -g 'daemon off;' &
nginx_pid=$!
sleeper=
shutdown() {
    trap - INT TERM
    [ -z "$sleeper" ] || kill "$sleeper" 2>/dev/null || true
    kill -QUIT "$nginx_pid" 2>/dev/null || true
    wait "$nginx_pid" || true
    exit 0
}
trap shutdown INT TERM
while kill -0 "$nginx_pid" 2>/dev/null; do
    sleep 15 &
    sleeper=$!
    wait "$sleeper" || true
    sleeper=
    next=$(fingerprint)
    # Keep serving the previous certificate if files disappear or validation fails.
    if [ "$next" != missing ] && [ "$next" != "$current" ]; then
        cp "$config" "$config.previous"
        render "$next"
        if nginx -t && nginx -s reload; then
            current=$next
            echo 'HTTPS certificate loaded.'
        else
            mv "$config.previous" "$config"
            echo 'Certificate reload failed; retaining the running configuration and retrying.' >&2
        fi
    fi
done
wait "$nginx_pid"
