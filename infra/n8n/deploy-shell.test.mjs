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

function run(t, failure = '', sudo = false) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-deploy-shell-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const calls = path.join(dir, 'calls');
  fs.writeFileSync(path.join(dir, 'sudo'), `#!/bin/sh
[ "$1" = -n ] && [ "$2" = /usr/bin/docker ] || exit 99
shift 2
unset N8N_INGEST_TOKEN SERVER_IMAGE
exec docker "$@"
`, { mode: 0o700 });
  fs.writeFileSync(path.join(dir, 'docker'), `#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_CALLS"
case "$*" in
  *'config --services')
    [ "$FAILURE" != config ] || exit 3
    printf 'mysql\\nserver\\nn8n\\n' ;;
  *'exec -T server printenv N8N_INGEST_TOKEN')
    [ "$FAILURE" != token ] || exit 1
    printf '%s\\n' "$EXPECTED_TOKEN" ;;
  *'ps --status running -q n8n') echo running-container ;;
  *'run --rm --no-deps -e N8N_INGEST_TOKEN n8n-workflows apply'*)
    [ "$N8N_INGEST_TOKEN" = "$EXPECTED_TOKEN" ] || exit 9
    echo 'token passed by environment' >> "$FAKE_CALLS"
    [ "$FAILURE" != import ] || exit 7 ;;
  *'run --rm --no-deps -T n8n-workflows apply'*'--token-stdin')
    [ -z "$N8N_INGEST_TOKEN" ] || exit 10
    [ "$(cat)" = "$EXPECTED_TOKEN" ] || exit 9
    echo 'token passed by stdin' >> "$FAKE_CALLS"
    [ "$FAILURE" != import ] || exit 7 ;;
esac
`, { mode: 0o700 });
  const token = 'shell-test-only-'.repeat(4);
  const script = fileURLToPath(new URL('./deploy.sh', import.meta.url));
  const result = spawnSync(bash, ['-c', 'export PATH="$1:$PATH"; exec sh "$2" apply',
    'deploy-test', shellPath(dir), shellPath(script)], {
    encoding: 'utf8', timeout: 15000,
    env: { ...process.env, FAKE_CALLS: shellPath(calls), EXPECTED_TOKEN: token,
      N8N_INGEST_TOKEN: '', FAILURE: failure, AIKNOW_DOCKER_SUDO: String(sudo) },
  });
  if (result.error) throw result.error;
  const log = fs.readFileSync(calls, 'utf8');
  assert.ok(!log.includes(token));
  assert.ok(!(result.stdout + result.stderr).includes(token));
  return { result, log };
}

test('production shell apply passes only the captured token through env and restarts n8n', t => {
  const { result, log } = run(t);
  assert.equal(result.status, 0, result.stderr);
  assert.match(log, /token passed by environment/);
  assert.match(log, /stop -t 60 n8n/);
  assert.match(log, /start n8n/);
});

test('credential import failure still restarts n8n and returns failure', t => {
  const { result, log } = run(t, 'import');
  assert.equal(result.status, 7);
  assert.match(log, /start n8n/);
});

test('server token read failure aborts before stopping n8n or importing', t => {
  const { result, log } = run(t, 'token');
  assert.equal(result.status, 1);
  assert.doesNotMatch(log, /stop -t|run --rm|start n8n/);
});

test('invalid Compose configuration cannot fall through to a deployment without a token', t => {
  const { result, log } = run(t, 'config');
  assert.equal(result.status, 3);
  assert.doesNotMatch(log, /stop -t|run --rm|start n8n/);
});

for (const failure of ['', 'import', 'token', 'config']) test(`sudo deployment preserves token secrecy and cleanup on ${failure || 'success'}`, t => {
  const { result, log } = run(t, failure, true);
  assert.equal(result.status, { '': 0, import: 7, token: 1, config: 3 }[failure], result.stderr);
  if (['', 'import'].includes(failure)) {
    assert.match(log, /token passed by stdin/);
    assert.match(log, /start n8n/);
  } else assert.doesNotMatch(log, /stop -t|run --rm|start n8n/);
});
