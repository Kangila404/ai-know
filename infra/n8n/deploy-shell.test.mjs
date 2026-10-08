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

function run(t, failure = '') {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-deploy-shell-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const calls = path.join(dir, 'calls');
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
esac
`, { mode: 0o700 });
  const token = 'shell-test-only-'.repeat(4);
  const script = fileURLToPath(new URL('./deploy.sh', import.meta.url));
  const result = spawnSync(bash, ['-c', 'export PATH="$1:$PATH"; exec sh "$2" apply',
    'deploy-test', shellPath(dir), shellPath(script)], {
    encoding: 'utf8', timeout: 15000,
    env: { ...process.env, FAKE_CALLS: shellPath(calls), EXPECTED_TOKEN: token,
      N8N_INGEST_TOKEN: '', FAILURE: failure },
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
