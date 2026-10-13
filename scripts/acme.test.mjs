import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
const shellPath = value => process.platform === 'win32'
  ? value.replaceAll('\\', '/').replace(/^([A-Za-z]):/, (_, drive) => '/' + drive.toLowerCase()) : value;
const root = fileURLToPath(new URL('../', import.meta.url));
function fixture(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-acme-test-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const calls = path.join(dir, 'calls');
  fs.writeFileSync(calls, '');
  return { dir, calls, write(name, content) { fs.writeFileSync(path.join(dir, name), content, { mode: 0o700 }); } };
}
function run(f, script, args = [], extra = {}) {
  const result = spawnSync(bash, ['-c', 'export PATH="$1:$PATH"; shift; exec sh "$@"',
    'acme-test', shellPath(f.dir), shellPath(script), ...args], {
    encoding: 'utf8', timeout: 15000,
    env: { ...process.env, FAKE_CALLS: shellPath(f.calls), API_DOMAIN: 'api.example.org',
      ACME_EMAIL: 'operator@example.org', ACME_AGREE_TOS: 'true', ...extra },
  });
  if (result.error) throw result.error;
  return { ...result, calls: fs.readFileSync(f.calls, 'utf8') };
}

test('issuance uses HTTP webroot, a stable lineage and reuses unexpired certificates', t => {
  const f = fixture(t);
  f.write('certbot', '#!/bin/sh\nprintf "%s\\n" "$*" >> "$FAKE_CALLS"\n');
  const r = run(f, path.join(root, 'infra/acme/certbot.sh'), ['issue']);
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.calls, /certonly .*--webroot --webroot-path \/var\/www\/acme/);
  assert.match(r.calls, /--cert-name api.example.org --domain api.example.org --keep-until-expiring/);
  assert.doesNotMatch(r.calls, /force-renewal|staging|register-unsafely/);
});

for (const [name, env] of Object.entries({
  'missing email': { ACME_EMAIL: '' },
  'unaccepted agreement': { ACME_AGREE_TOS: 'false' },
  'unsafe domain': { API_DOMAIN: 'example.org; return 200;' },
})) test(`issuance rejects ${name} before contacting the CA`, t => {
  const f = fixture(t);
  f.write('certbot', '#!/bin/sh\necho called >> "$FAKE_CALLS"\n');
  const r = run(f, path.join(root, 'infra/acme/certbot.sh'), ['issue'], env);
  assert.equal(r.status, 2);
  assert.equal(r.calls, '');
});

function deployment(t, failure = '', sudo = false) {
  const f = fixture(t);
  f.write('sudo', `#!/bin/sh
[ "$1" = -n ] && [ "$2" = /usr/bin/docker ] || exit 99
shift 2
unset SERVER_IMAGE N8N_INGEST_TOKEN
exec docker "$@"
`);
  f.write('docker', `#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_CALLS"
if [ "$1" = compose ]; then
  [ "$2" = --env-file ] && [ "$4" = --env-file ] || exit 88
  grep -qx "SERVER_IMAGE=$EXPECTED_IMAGE" "$5" || exit 89
fi
case "$*" in
  *'certbot check') [ "$FAILURE" != check ] || exit 3 ;;
  *'certbot issue') [ "$FAILURE" != issue ] || exit 4 ;;
  *'exec -T mysql '*) echo '-- test database backup' ;;
  *'config --services') echo server ;;
  *'exec -T server printenv N8N_INGEST_TOKEN') printf 'test-ingestion-token-with-at-least-32-characters' ;;
  *'ps -q server') echo previous-container ;;
  'inspect '*) echo previous-image ;;
  *'--token-stdin') [ "$(cat)" = test-ingestion-token-with-at-least-32-characters ] || exit 90 ;;
esac
`);
  const envFile = path.join(f.dir, 'infra.env');
  f.write('infra.env', '');
  const r = run(f, path.join(root, 'infra/deploy-server.sh'), [shellPath(envFile),
    `docker.io/example/aiknow-server:${'a'.repeat(40)}`], { FAILURE: failure,
      EXPECTED_IMAGE: `docker.io/example/aiknow-server:${'a'.repeat(40)}`, AIKNOW_DOCKER_SUDO: String(sudo) });
  assert.ok(!fs.readdirSync(f.dir).some(name => name.startsWith('.release-env.')), 'release env must be cleaned on success and failure');
  return { ...r, dir: f.dir };
}

test('first deployment bootstraps HTTP, issues TLS, starts renewal, then backs up and starts app', t => {
  const r = deployment(t);
  assert.equal(r.status, 0, r.stderr);
  const steps = ['certbot check', 'up -d --no-deps --wait --wait-timeout 90 proxy',
    'stop certbot', 'certbot issue', 'up -d --no-deps certbot', 'wait-https.sh',
    'up -d --wait --wait-timeout 180 mysql', 'exec -T mysql', 'up -d --wait --wait-timeout 180 server proxy n8n'];
  let previous = -1;
  for (const step of steps) {
    const index = r.calls.indexOf(step);
    assert.ok(index > previous, `Expected ordered step: ${step}`);
    previous = index;
  }
  assert.ok(fs.existsSync(path.join(r.dir, 'current-server-image')));
});

for (const failure of ['', 'check', 'issue']) test(`sudo deployment retains verified image after env reset on ${failure || 'success'}`, t => {
  const r = deployment(t, failure, true);
  assert.equal(r.status, { '': 0, check: 3, issue: 1 }[failure], r.stderr);
  assert.match(r.calls, /inspect --format/);
  if (!failure) assert.match(r.calls, /--token-stdin/);
});

test('missing ACME configuration aborts before service changes or database work', t => {
  const r = deployment(t, 'check');
  assert.equal(r.status, 3);
  assert.doesNotMatch(r.calls, /up -d|stop certbot|exec -T mysql/);
  assert.ok(!fs.existsSync(path.join(r.dir, 'current-server-image')));
});

test('CA failure restores renewal service but aborts before database and app replacement', t => {
  const r = deployment(t, 'issue');
  assert.equal(r.status, 1);
  assert.match(r.calls, /up -d --no-deps certbot/);
  assert.doesNotMatch(r.calls, /exec -T mysql|180 mysql|180 server/);
  assert.ok(!fs.existsSync(path.join(r.dir, 'current-server-image')));
});

for (const invalidFirst of [false, true]) test(`proxy switches from HTTP to HTTPS${invalidFirst ? ' after rejecting an invalid certificate' : ''}`, t => {
  const f = fixture(t);
  const certs = path.join(f.dir, 'certs');
  fs.mkdirSync(path.join(certs, 'live/api.example.org'), { recursive: true });
  const config = path.join(f.dir, 'default.conf');
  const templates = path.join(root, 'infra/nginx');
  // Relocate only filesystem paths so the production shell runs without root or Docker.
  f.write('start.sh', fs.readFileSync(path.join(templates, 'start.sh'), 'utf8')
    .replaceAll('/etc/letsencrypt', shellPath(certs))
    .replaceAll('/etc/nginx/conf.d/default.conf', shellPath(config))
    .replaceAll('/opt/aiknow/nginx', shellPath(templates)));
  f.write('envsubst', '#!/bin/sh\nsed \'s/${API_DOMAIN}/api.example.org/g\'\n');
  f.write('nginx', `#!/bin/sh
case "$1" in
  -t)
    if grep -q 'listen 443 ssl' "$TEST_CONFIG"; then
      if grep -q invalid "$TEST_CERTS/fullchain.pem"; then echo rejected >> "$FAKE_CALLS"; exit 1; fi
      echo validated-https >> "$FAKE_CALLS"
    else echo validated-http >> "$FAKE_CALLS"; fi ;;
  -g) exec /bin/sleep 1 ;;
  -s) echo reloaded >> "$FAKE_CALLS"; kill -TERM "$PPID" ;;
esac
`);
  f.write('sleep', `#!/bin/sh
if [ ! -e "$TEST_CERTS/fullchain.pem" ]; then
  printf '%s' "$FIRST_CERT" > "$TEST_CERTS/fullchain.pem"
  echo test-only-private-key > "$TEST_CERTS/privkey.pem"
elif grep -q invalid "$TEST_CERTS/fullchain.pem"; then
  grep -q 'return 503' "$TEST_CONFIG" && echo retained-http >> "$FAKE_CALLS"
  echo valid > "$TEST_CERTS/fullchain.pem"
fi
exec /bin/sleep 0.05
`);
  const r = run(f, path.join(f.dir, 'start.sh'), [], {
    TEST_CONFIG: shellPath(config), TEST_CERTS: shellPath(path.join(certs, 'live/api.example.org')),
    FIRST_CERT: invalidFirst ? 'invalid' : 'valid',
  });
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.calls, /validated-http/);
  assert.match(r.calls, /validated-https\nreloaded/);
  if (invalidFirst) assert.match(r.calls, /rejected\nretained-http/);
  const final = fs.readFileSync(config, 'utf8');
  assert.match(final, /listen 443 ssl/);
  assert.match(final, /live\/api.example.org\/fullchain.pem/);
  assert.match(final, /proxy_pass \$backend/);
  assert.doesNotMatch(final, /\$\{API_DOMAIN\}/);
});

test('renewal uses persisted certificate settings and fails if no certificate was issued', t => {
  const f = fixture(t);
  const certs = path.join(f.dir, 'certs');
  fs.mkdirSync(path.join(certs, 'live/api.example.org'), { recursive: true });
  f.write('certbot.sh', fs.readFileSync(path.join(root, 'infra/acme/certbot.sh'), 'utf8')
    .replaceAll('/etc/letsencrypt', shellPath(certs)));
  f.write('certbot', '#!/bin/sh\nprintf "%s\\n" "$*" >> "$FAKE_CALLS"\n');
  const missing = run(f, path.join(f.dir, 'certbot.sh'), ['renew-once']);
  assert.equal(missing.status, 1);
  assert.equal(missing.calls, '');
  fs.writeFileSync(path.join(certs, 'live/api.example.org/fullchain.pem'), 'test-certificate');
  const existing = run(f, path.join(f.dir, 'certbot.sh'), ['renew-once']);
  assert.equal(existing.status, 0, existing.stderr);
  assert.match(existing.calls, /^renew --non-interactive --cert-name api.example.org --webroot/);
});
