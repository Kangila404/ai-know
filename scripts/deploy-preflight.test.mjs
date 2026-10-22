import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
const shellPath = value => process.platform === 'win32'
  ? value.replaceAll('\\', '/').replace(/^([A-Za-z]):/, (_, drive) => '/' + drive.toLowerCase()) : value;

for (const sudo of [false, true]) for (const failure of ['missing-directory', 'missing-env', 'empty-env', 'docker', 'compose', 'disk', 'inodes', 'none']) {
  test(`NAS preflight identifies ${failure} (sudo=${sudo}) without printing env contents`, t => {
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
if [ "$1" = info ] && [ "$2" = --format ]; then printf '%s\\n' "$TEST_DOCKER_ROOT"; fi
exit 0
`, { mode: 0o700 });
    fs.writeFileSync(path.join(dir, 'df'), `#!/bin/sh
available=4194304
if [ "$1" = -Pk ] && [ "$FAILURE" = disk ]; then available=404000; fi
if [ "$1" = -Pi ] && [ "$FAILURE" = inodes ]; then available=0; fi
printf 'Filesystem Total Used Available Use%% Mounted\\n'
printf 'testfs 9999999 1 %s 1%% /\\n' "$available"
`, { mode: 0o700 });
    fs.writeFileSync(path.join(dir, 'sudo'), `#!/bin/sh
[ "$1" = -n ] && [ "$2" = /usr/bin/docker ] || exit 99
shift 2
exec docker "$@"
`, { mode: 0o700 });
    fs.writeFileSync(path.join(dir, 'check.sh'), fs.readFileSync(new URL('../infra/docker-command.sh', import.meta.url), 'utf8') + '\n' + fs.readFileSync(new URL('../infra/check-deploy-host.sh', import.meta.url), 'utf8')
      .replaceAll('/opt/aiknow', shellPath(nas)));
    const r = spawnSync(bash, ['-c', 'export PATH="$1:$PATH"; exec sh "$1/check.sh"', 'test', shellPath(dir)], {
      env: { ...process.env, FAILURE: failure, TEST_DOCKER_ROOT: shellPath(nas), AIKNOW_DOCKER_SUDO: String(sudo) }, encoding: 'utf8', timeout: 10000,
    });
    if (r.error) throw r.error;
    assert.equal(r.status, failure === 'none' ? 0 : 1, r.stderr);
    const expected = {
      'missing-directory': /persistent deployment path/, 'missing-env': /server.env is missing/,
      'empty-env': /server.env is empty/, docker: /Docker is unavailable/,
      compose: /Compose plugin is unavailable/, disk: /Less than 2 GiB/, inodes: /No free inodes/, none: /prerequisites passed/,
    };
    assert.match(r.stdout + r.stderr, expected[failure]);
    assert.ok(!(r.stdout + r.stderr).includes(secret));
  });
}
