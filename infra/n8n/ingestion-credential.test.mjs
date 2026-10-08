import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { main } from './deploy-workflow.mjs';
import { ingestionCredential, bindIngestionCredential, syncIngestionCredential } from './ingestion-credential.mjs';

const token = 'test-only-token-'.repeat(4);
const env = {
  AIKNOW_ENV: 'prod', AIKNOW_SERVER_BASE_URL: 'http://server:8080',
  AIKNOW_MEDIA_PUBLIC_BASE_URL: 'https://media.example.invalid/media/generated',
  AIKNOW_DRY_RUN: 'true', AIKNOW_ALLOW_IMAGE_GENERATION: 'false', AIKNOW_MAX_IMAGE_GENERATIONS: '1',
  N8N_INGEST_TOKEN: token,
};
const filename = 'ai-know-news-draft.json';
const sourceFile = new URL(`./workflows/${filename}`, import.meta.url);

function harness(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-credential-test-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  fs.copyFileSync(sourceFile, path.join(dir, filename));
  const db = { workflow: null, credentials: new Map(), calls: [], tempFiles: [] };
  const cli = args => {
    db.calls.push(args);
    const option = name => args.find(arg => arg.startsWith(`--${name}=`))?.slice(name.length + 3);
    if (args[0] === 'export:workflow') {
      fs.writeFileSync(option('output'), JSON.stringify(db.workflow ? [db.workflow] : []));
    } else if (args[0] === 'import:workflow') {
      [db.workflow] = JSON.parse(fs.readFileSync(option('input'), 'utf8'));
      db.workflow.shared = [{ role: 'workflow:owner', projectId: 'owner-project' }];
    } else if (args[0] === 'import:credentials') {
      const [credential] = JSON.parse(fs.readFileSync(option('input'), 'utf8'));
      db.credentials.set(credential.id, credential);
      db.tempFiles.push(option('input'));
    } else if (args[0] === 'export:credentials') {
      const credential = db.credentials.get(option('id'));
      fs.writeFileSync(option('output'), JSON.stringify(credential ? [credential] : []));
      db.tempFiles.push(option('output'));
    } else throw new Error('Unexpected CLI command');
    return '';
  };
  const runtime = { env: { ...env }, cli, sourceDirectory: dir, stateDirectory: path.join(dir, 'state') };
  const apply = () => main(['apply', filename], runtime);
  return { dir, db, runtime, apply };
}

test('first deployment provisions one credential and binds every internal API without embedding the token', t => {
  const { db, dir, apply } = harness(t);
  apply();
  assert.equal(db.credentials.size, 1);
  const internal = db.workflow.nodes.filter(n => typeof n.parameters?.url === 'string'
    && n.parameters.url.startsWith('http://server:8080/internal/'));
  assert.equal(internal.length, 8);
  const [credential] = db.credentials.values();
  for (const node of internal) assert.equal(node.credentials.httpHeaderAuth.id, credential.id);
  for (const node of db.workflow.nodes.filter(n => !internal.includes(n))) {
    assert.notEqual(node.credentials?.httpHeaderAuth?.id, credential.id);
  }
  assert.equal(credential.data.value, `Bearer ${token}`);
  assert.equal(db.workflow.active, false);
  assert.equal(db.workflow.activeVersionId, null);
  assert.ok(!JSON.stringify(db.workflow).includes(token));
  assert.ok(!fs.readFileSync(path.join(dir, 'state', `${filename}.state.json`), 'utf8').includes(token));
  assert.ok(db.tempFiles.every(file => !fs.existsSync(file)));
});

test('token rotation updates the same credential without importing or unpublishing the workflow', t => {
  const { db, runtime, apply } = harness(t);
  apply();
  db.workflow.active = true;
  db.workflow.activeVersionId = 'published-version';
  db.workflow.staticData = { global: { processedUrls: ['already-saved'] } };
  const model = db.workflow.nodes.find(n => n.credentials?.httpHeaderAuth === undefined);
  model.credentials = { openAiApi: { id: 'keep-openai', name: 'OpenAI' } };
  const before = structuredClone(db.workflow);
  runtime.env.N8N_INGEST_TOKEN = 'rotated-test-token-'.repeat(4);
  db.calls = [];
  apply();
  assert.equal(db.credentials.size, 1);
  assert.equal([...db.credentials.values()][0].data.value, `Bearer ${runtime.env.N8N_INGEST_TOKEN}`);
  assert.deepEqual(db.workflow, before);
  assert.ok(!db.calls.some(args => args[0] === 'import:workflow'));
  assert.ok(db.calls.find(args => args[0] === 'import:credentials').includes('--projectId=owner-project'));
});

test('existing manual bindings migrate to a draft while preserving UI edits, OpenAI and history', t => {
  const { db, runtime, dir, apply } = harness(t);
  // Simulate a deployment before automatic provisioning existed.
  runtime.env.N8N_INGEST_TOKEN = undefined;
  runtime.env.AIKNOW_ENV = 'local';
  apply();
  runtime.env.N8N_INGEST_TOKEN = token;
  const recordPath = path.join(dir, 'state', `${filename}.state.json`);
  const baseline = fs.readFileSync(recordPath, 'utf8');
  db.workflow.name = 'Edited in UI';
  db.workflow.staticData = { global: { processedUrls: ['keep'] } };
  const unrelated = db.workflow.nodes.find(n => n.name === '실행 설정');
  unrelated.credentials = { openAiApi: { id: 'keep', name: 'Keep' } };
  apply();
  assert.equal(db.workflow.name, 'Edited in UI');
  assert.deepEqual(db.workflow.staticData, { global: { processedUrls: ['keep'] } });
  assert.deepEqual(db.workflow.nodes.find(n => n.id === unrelated.id).credentials, unrelated.credentials);
  assert.equal(fs.readFileSync(recordPath, 'utf8'), baseline);
  assert.equal(db.workflow.active, false);
});

test('check does not provision credentials and missing/short production tokens cannot apply', t => {
  const { db, runtime, apply } = harness(t);
  main(['check', filename], runtime);
  assert.ok(db.calls.every(args => args[0] === 'export:workflow'));
  runtime.env.N8N_INGEST_TOKEN = '';
  assert.throws(apply, /running production server/);
  runtime.env.N8N_INGEST_TOKEN = 'too-short';
  assert.throws(apply, /32 non-whitespace/);
  assert.equal(db.credentials.size, 0);
});

test('edited backend URLs and enabled redirects fail before updating any credential', t => {
  const { db, apply } = harness(t);
  apply();
  const node = db.workflow.nodes.find(n => n.id === 'aiknow-server-import');
  const original = node.parameters.url;
  node.parameters.url = 'https://untrusted.example/internal/v1/card-news/import';
  db.calls = [];
  assert.throws(apply, /Internal API node settings differ/);
  assert.ok(!db.calls.some(args => args[0] === 'import:credentials'));
  node.parameters.url = original;
  node.parameters.options.redirect.redirect.followRedirects = true;
  assert.throws(apply, /Internal API node settings differ/);
});

test('credential import failures are verified, sanitized and temporary secrets removed', t => {
  const { dir } = harness(t);
  const credential = ingestionCredential('workflow', token);
  let input;
  const cli = args => {
    if (args[0] === 'import:credentials') input = args[1].slice('--input='.length);
    else fs.writeFileSync(args.find(a => a.startsWith('--output=')).slice('--output='.length), token);
    return ''; // Simulate status-zero CLI failure/malformed export.
  };
  assert.throws(() => syncIngestionCredential(credential, null, dir, cli), error => {
    assert.ok(!error.message.includes(token));
    return /synchronization failed/.test(error.message);
  });
  assert.equal(fs.existsSync(input), false);
  assert.equal(fs.existsSync(path.join(dir, 'ingestion-credential-check.json')), false);
});

test('no generic external HTTP node can receive the managed token', () => {
  const credential = ingestionCredential('workflow', token);
  assert.throws(() => bindIngestionCredential({ nodes: [{ id: 'arbitrary', parameters: {} }] },
    env.AIKNOW_SERVER_BASE_URL, credential), /Missing internal/);
  assert.throws(() => ingestionCredential('workflow', token + '\n'), /non-whitespace/);
});
