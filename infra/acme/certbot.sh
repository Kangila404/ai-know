#!/bin/sh
set -eu
validate() {
    printf '%s\n' "${API_DOMAIN:-}" | LC_ALL=C grep -Eq '^([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$' || {
        echo 'API_DOMAIN must be a DNS hostname.' >&2; exit 2;
    }
    case "${ACME_EMAIL:-}" in
        ?*@?*.?*) ;;
        *) echo 'Set ACME_EMAIL to the certificate account contact email.' >&2; exit 2;;
    esac
    [ "${ACME_AGREE_TOS:-false}" = true ] || {
        echo 'Read https://letsencrypt.org/repository/ and set ACME_AGREE_TOS=true after accepting the subscriber agreement.' >&2; exit 2;
    }
}
issue() {
    validate
    # keep-until-expiring reuses valid certificates and avoids reissuing on every CD.
    certbot certonly --non-interactive --agree-tos --email "$ACME_EMAIL" \
        --webroot --webroot-path /var/www/acme --cert-name "$API_DOMAIN" \
        --domain "$API_DOMAIN" --keep-until-expiring
}
renew() {
    test -s "/etc/letsencrypt/live/$API_DOMAIN/fullchain.pem" || {
        echo 'No certificate exists yet. Run a successful deployment to issue it first.' >&2; return 1;
    }
    certbot renew --non-interactive --cert-name "$API_DOMAIN" --webroot --webroot-path /var/www/acme
}
case "${1:-}" in
    check) validate ;;
    issue) issue ;;
    renew-once) validate; renew ;;
    renew-loop)
        validate
        sleeper=
        trap '[ -z "$sleeper" ] || kill "$sleeper" 2>/dev/null || true; exit 0' INT TERM
        while :; do
            if renew; then
                touch /tmp/renew-ok
            else
                echo 'Certificate renewal failed; inspect certbot logs and public DNS/port 80. Retrying in 12 hours.' >&2
            fi
            sleep 43200 &
            sleeper=$!
            wait "$sleeper" || true
            sleeper=
        done
        ;;
    *) echo 'Usage: certbot.sh check|issue|renew-once|renew-loop' >&2; exit 2 ;;
esac
