// Real MySQL regression check. Uses an isolated container/anonymous volume, no host ports.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const name = `aiknow-mysql-readiness-${randomUUID()}`;
const compose = fs.readFileSync(new URL('../infra/docker-compose.prod.yml', import.meta.url), 'utf8');
const probe = compose.match(/test: \[CMD-SHELL, '([^']+)'\]/)[1].replaceAll('$$', '$');
const deploy = fs.readFileSync(new URL('../infra/deploy-server.sh', import.meta.url), 'utf8');
const backup = deploy.match(/compose exec -T mysql sh -c '([^']+)'/)[1];
// Synthetic credentials, deliberately containing shell metacharacters.
const password = 'test-only $dollar "quote" space';
function docker(args, { allowFailure = false, timeout = 30000 } = {}) {
  const r = spawnSync('docker', args, { encoding: 'utf8', timeout, maxBuffer: 2 * 1024 * 1024 });
  if (r.error) throw r.error;
  if (!allowFailure && r.status !== 0) throw new Error(r.stderr || `Docker exited ${r.status}`);
  return r;
}

try {
  console.log('Starting a fresh MySQL 8.0 database with the production health probe...');
  docker(['run', '-d', '--name', name,
    '-e', `MYSQL_ROOT_PASSWORD=${password}`, '-e', 'MYSQL_DATABASE=probe_db',
    '--health-cmd', probe, '--health-interval', '2s', '--health-timeout', '5s', '--health-retries', '90',
    'mysql:8.0'], { timeout: 180000 });
  const deadline = Date.now() + 180000;
  let healthy = false;
  while (Date.now() < deadline) {
    const state = docker(['inspect', '--format', '{{.State.Status}} {{.State.Health.Status}}', name]).stdout.trim();
    if (state === 'running healthy') { healthy = true; break; }
    if (state.startsWith('exited') || state.endsWith('unhealthy')) throw new Error(`MySQL failed startup: ${state}`);
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  assert.ok(healthy, 'MySQL must authenticate within the readiness timeout');
  const dumped = docker(['exec', name, 'sh', '-c', backup]);
  assert.match(dumped.stdout, /Dump completed/, 'First backup must succeed immediately after readiness');
  const denied = docker(['exec', '-e', 'MYSQL_ROOT_PASSWORD=deliberately-wrong', name, 'sh', '-c', probe], { allowFailure: true });
  assert.notEqual(denied.status, 0, 'Authentication errors must fail health checks');
  const missing = docker(['exec', '-e', 'MYSQL_DATABASE=missing_database', name, 'sh', '-c', probe], { allowFailure: true });
  assert.notEqual(missing.status, 0, 'The configured database must exist');
  console.log('PASS: first-install readiness, immediate backup, wrong-password rejection and missing-database rejection.');
} finally {
  // This name was generated above solely for this test; -v removes only its anonymous test volume.
  docker(['rm', '-f', '-v', name], { allowFailure: true });
}
