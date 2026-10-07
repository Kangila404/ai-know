import test from 'node:test';
import assert from 'node:assert/strict';
import { decision, fingerprint, importPayload, validate } from './deploy-workflow.mjs';

const workflow = () => ({
  id: 'stable-workflow', name: 'News', active: true,
  nodes: [{ id: 'node-1', name: 'Summary', type: 'n8n-nodes-base.httpRequest',
    parameters: { url: 'https://example.com' }, position: [0, 0],
    credentials: { openAiApi: { id: 'source-secret-reference', name: 'Source account' } } }],
  connections: {}, settings: { timezone: 'Asia/Seoul' },
});
const record = source => ({ workflowId: source.id, sourceHash: fingerprint(source), liveHash: fingerprint(source) });

test('first import, unchanged source and changed source select the expected deployment', () => {
  const source = workflow();
  assert.equal(decision(source, null, null), 'import');
  assert.equal(decision(source, structuredClone(source), null), 'adopt');
  const baseline = record(source);
  const next = { ...source, name: 'Updated news' };
  assert.equal(decision(next, source, baseline), 'import');
  assert.equal(decision(source, next, baseline), 'unchanged'); // Preserve later UI edits.
});

test('untracked existing content and conflicting UI changes require explicit replacement', () => {
  const source = workflow();
  const edited = { ...source, name: 'UI edit' };
  const next = { ...source, name: 'Git edit' };
  assert.throws(() => decision(source, edited, null), /differs/);
  assert.throws(() => decision(next, edited, record(source)), /differs/);
  assert.equal(decision(next, edited, record(source), true), 'import');
  assert.throws(() => decision({ ...source, id: 'different' }, null, record(source), true), /id changed/);
});

test('credential changes, activation and runtime state do not cause redeployment', () => {
  const source = workflow();
  const live = structuredClone(source);
  live.active = false;
  live.staticData = { global: { processedUrls: ['https://example.com/news'] } };
  live.nodes[0].credentials.openAiApi.id = 'target-account';
  live.pinData = { Summary: [{ json: { personalData: 'test' } }] };
  live.versionId = 'new-version';
  assert.equal(fingerprint(source), fingerprint(live));
  assert.equal(fingerprint(source), fingerprint({ ...source, settings: { timezone: 'Asia/Seoul' } }));
});

test('imports are drafts and preserve only target credential bindings and processing history', () => {
  const source = workflow();
  const live = structuredClone(source);
  live.nodes[0].credentials.openAiApi = { id: 'target-account', name: 'Server OpenAI' };
  live.staticData = { global: { processedUrls: ['already-processed'] } };
  const first = importPayload(source, null);
  assert.equal(first.nodes[0].credentials, undefined);
  assert.equal(first.active, false);
  assert.equal(first.activeVersionId, null);
  const update = importPayload(source, live);
  assert.deepEqual(update.nodes[0].credentials, live.nodes[0].credentials);
  assert.deepEqual(update.staticData, live.staticData);
  assert.deepEqual(update.pinData, {});
  live.nodes[0].type = 'different-node-type';
  assert.equal(importPayload(source, live).nodes[0].credentials, undefined);
});

test('rejects missing identity and duplicate nodes before touching n8n', () => {
  validate(workflow());
  assert.throws(() => validate({ ...workflow(), id: '../escape' }), /stable id/);
  const duplicated = workflow();
  duplicated.nodes.push(structuredClone(duplicated.nodes[0]));
  assert.throws(() => validate(duplicated), /unique/);
});

test('new media endpoints inherit only the same backend ingestion credential', () => {
  const live = workflow();
  live.nodes.push({ id: 'aiknow-server-import', type: 'n8n-nodes-base.httpRequest',
    parameters: { url: 'http://server:8080/internal/v1/card-news/import' },
    credentials: { httpHeaderAuth: { id: 'target-only', name: 'Ingestion' } } });
  const source = structuredClone(live);
  source.nodes.push({ id: 'aiknow-image-upload', type: 'n8n-nodes-base.httpRequest',
    parameters: { url: 'http://server:8080/internal/v1/images' } });
  assert.equal(importPayload(source, live).nodes.at(-1).credentials.httpHeaderAuth.id, 'target-only');
  assert.equal(importPayload(source, null).nodes.at(-1).credentials, undefined);
  source.nodes.at(-1).parameters.url = 'https://different-server/internal/v1/images';
  assert.equal(importPayload(source, live).nodes.at(-1).credentials, undefined);
});
