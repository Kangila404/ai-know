import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
const shellPath = value => process.platform === 'win32'
  ? value.replaceAll('\\', '/').replace(/^([A-Za-z]):/, (_, drive) => '/' + drive.toLowerCase()) : value;

for (const failure of ['missing-directory', 'missing-env', 'empty-env', 'docker', 'compose', 'none']) {
  test(`NAS preflight identifies ${failure} without printing env contents`, t => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-preflight-'));
    t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
    const nas = path.join(dir, 'nas');
    const secret = 'test-env-content-must-not-appear-in-logs';
    if (failure !== 'missing-directory') {
      fs.mkdirSync(nas);
      fs.writeFileSync(path.join(nas, 'infra.env'), secret);
      if (failure !== 'missing-env') fs.writeFileSync(path.join(nas, 'server.env'), failure === 'empty-env' ? '' : secret);
    }
    fs.writeFileSync(path.join(dir, 'docker'), `#!/bin/sh
if [ "$1" = info ] && [ "$FAILURE" = docker ]; then exit 1; fi
if [ "$1" = compose ] && [ "$FAILURE" = compose ]; then exit 1; fi
exit 0
`, { mode: 0o700 });
    fs.writeFileSync(path.join(dir, 'check.sh'), fs.readFileSync(new URL('../infra/check-deploy-host.sh', import.meta.url), 'utf8')
      .replaceAll('/opt/aiknow', shellPath(nas)));
    const r = spawnSync(bash, ['-c', 'export PATH="$1:$PATH"; exec sh "$1/check.sh"', 'test', shellPath(dir)], {
      env: { ...process.env, FAILURE: failure }, encoding: 'utf8', timeout: 10000,
    });
    if (r.error) throw r.error;
    assert.equal(r.status, failure === 'none' ? 0 : 1, r.stderr);
    const expected = {
      'missing-directory': /persistent deployment path/, 'missing-env': /server.env is missing/,
      'empty-env': /server.env is empty/, docker: /Docker is unavailable/,
      compose: /Compose plugin is unavailable/, none: /prerequisites passed/,
    };
    assert.match(r.stdout + r.stderr, expected[failure]);
    assert.ok(!(r.stdout + r.stderr).includes(secret));
  });
}
