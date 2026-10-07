import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { configureWorkflow, readWorkflowEnvironment } from './workflow-environment.mjs';
import { fingerprint } from './deploy-workflow.mjs';

const env = { AIKNOW_ENV: 'local', AIKNOW_SERVER_BASE_URL: 'http://host.docker.internal:8080',
  AIKNOW_MEDIA_PUBLIC_BASE_URL: 'http://localhost:8080/media/generated', AIKNOW_DRY_RUN: 'false',
  AIKNOW_ALLOW_IMAGE_GENERATION: 'true', AIKNOW_MAX_IMAGE_GENERATIONS: '1' };
const source = () => JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json', import.meta.url)));

test('local env is applied without editing source, credentials, schedules or unrelated settings', () => {
  const original = source(), before = structuredClone(original), local = configureWorkflow(original, env);
  assert.deepEqual(original, before);
  const config = new Function(local.nodes.find(n => n.name === '실행 설정').parameters.jsCode)()[0].json.config;
  assert.equal(config.allowImageGeneration, true); assert.equal(config.dryRun, false);
  assert.equal(config.maxImageGenerations, 1); assert.equal(config.textModel, 'gpt-6-luna');
  assert.deepEqual(local.nodes.filter(n => n.type.endsWith('.scheduleTrigger')), original.nodes.filter(n => n.type.endsWith('.scheduleTrigger')));
  assert.deepEqual(local.nodes.map(n => n.credentials), original.nodes.map(n => n.credentials));
  assert.match(local.nodes.find(n => n.id === 'aiknow-upload-done').parameters.jsCode, /localhost:8080\/media\/generated/);
  assert.notEqual(fingerprint(local), fingerprint(original));
  assert.equal(fingerprint(configureWorkflow(original, env)), fingerprint(local));
});

test('prod env rejects HTTP media and malformed flags before deployment, routes all backend nodes consistently', () => {
  assert.throws(() => readWorkflowEnvironment({ ...env, AIKNOW_ENV: 'prod' }), /HTTPS/);
  assert.throws(() => readWorkflowEnvironment({ ...env, AIKNOW_ALLOW_IMAGE_GENERATION: 'yes' }), /true or false/);
  assert.throws(() => readWorkflowEnvironment({ ...env, AIKNOW_MAX_IMAGE_GENERATIONS: '100' }), /0-3/);
  const prod = configureWorkflow(source(), { ...env, AIKNOW_ENV: 'prod', AIKNOW_SERVER_BASE_URL: 'http://server:8080',
    AIKNOW_MEDIA_PUBLIC_BASE_URL: 'https://api.example.test/media/generated', AIKNOW_DRY_RUN: 'true', AIKNOW_ALLOW_IMAGE_GENERATION: 'false' });
  for (const node of prod.nodes.filter(n => n.parameters?.url?.includes('/internal/v1/')))
    assert.match(node.parameters.url, /^http:\/\/server:8080\/internal\/v1\//);
  assert.match(prod.nodes.find(n => n.id === 'aiknow-storage-check-done').parameters.jsCode, /"mode":"mounted"/);
  assert.notEqual(fingerprint(prod), fingerprint(configureWorkflow(source(), env)));
});
