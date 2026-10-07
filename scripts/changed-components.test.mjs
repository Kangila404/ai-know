import test from 'node:test';
import assert from 'node:assert/strict';
import { components } from './changed-components.mjs';

test('documentation-only changes skip expensive jobs', () => {
  assert.deepEqual(components(['server/docs/social-login.md', 'infra/PRODUCTION.md']), {server:false,workflow:false,infra:false});
});
test('backend, workflow and CI changes select their required checks', () => {
  assert.deepEqual(components(['server/src/main/java/App.java']), {server:true,workflow:false,infra:false});
  assert.deepEqual(components(['infra/n8n/workflows/ai-know-news-draft.json']), {server:false,workflow:true,infra:true});
  assert.deepEqual(components(['.github/workflows/server-release.yml']), {server:true,workflow:true,infra:true});
  assert.deepEqual(components(['server/src/main/resources/db/migration/V3.sql']), {server:true,workflow:false,infra:false});
});
