// Run with n8n stopped, using the n8n-workflows one-off container and a token on stdin.
// Sends one explicitly labelled test submission. No RSS, LLM, images or push calls.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { ingestionNodes, ingestionConnections } from './ingestion-nodes.mjs';

const { token } = JSON.parse(fs.readFileSync(0, 'utf8'));
if (typeof token !== 'string' || token.length < 32) throw new Error('Ingestion token required on stdin');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-ingest-check-'));
const save = (name, data) => {
  const file = path.join(temp, name);
  fs.writeFileSync(file, JSON.stringify(data), { mode: 0o600 });
  return file;
};
function cli(args, allowFailure = false) {
  const result = spawnSync('n8n', args, { encoding: 'utf8', timeout: 180000, maxBuffer: 16 * 1024 * 1024 });
  if (result.error || (!allowFailure && result.status !== 0)) throw new Error(`n8n ${args[0]} failed (diagnostics suppressed to protect credentials)`);
  return result;
}
function execute() {
  const result = cli(['execute', '--id=aiknow-ingestion-smoke', '--rawOutput'], true);
  for (const candidate of result.stdout.match(/^\{[\s\S]*?^\}/gm) ?? []) {
    try {
      const value = JSON.parse(candidate);
      if (value.data?.resultData) return value;
    } catch { /* Non-execution CLI output. */ }
  }
  throw new Error(`Execution did not return structured data (exit ${result.status})`);
}
const importWorkflow = workflow => cli(['import:workflow', `--input=${save('workflow.json', [workflow])}`, '--activeState=false']);

try {
  const credential = { id: 'aiknow-local-ingest', name: 'AI_KNOW Ingestion', type: 'httpHeaderAuth',
    data: { name: 'Authorization', value: `Bearer ${token}` } };
  cli(['import:credentials', `--input=${save('credential.json', [credential])}`]);
  const exported = path.join(temp, 'existing.json');
  cli(['export:workflow', '--id=iwGOpyXhwAq9tON2', `--output=${exported}`]);
  const [mainWorkflow] = JSON.parse(fs.readFileSync(exported, 'utf8'));
  const target = mainWorkflow.nodes.find(n => n.id === 'aiknow-server-import');
  assert.ok(target, 'Deploy the import nodes before running this check');
  target.credentials = { httpHeaderAuth: { id: credential.id, name: credential.name } };
  importWorkflow(mainWorkflow);

  const runId = new Date().toISOString().replace(/[:.]/g, '-');
  const draft = { sourceTitle: '[LOCAL E2E TEST] n8n to Spring to MySQL',
    sourceUrl: `https://example.com/aiknow/local-ingestion-check/${runId}`,
    publishedAt: new Date().toISOString(), title: '[로컬 테스트] n8n DB 저장 확인',
    summary: '실제 뉴스가 아닌 로컬 연결 검증용 초안입니다. 승인하거나 발송하지 마세요.',
    slides: Array.from({ length: 5 }, (_, i) => ({ sequence: i + 1, title: `연결 검증 ${i + 1}`,
      content: 'n8n 실행에서 Spring 수집 API를 거쳐 MySQL 검수 대기로 저장하는 테스트입니다.',
      visual: { type: 'text', image: null } })) };
  const workflow = { id: 'aiknow-ingestion-smoke', name: 'AI_KNOW [LOCAL TEST] 서버 저장 확인', active: false,
    settings: { executionOrder: 'v1', timezone: 'Asia/Seoul' },
    nodes: [
      { id: 'smoke-manual', name: '수동 실행', type: 'n8n-nodes-base.manualTrigger', typeVersion: 1, position: [0, 0], parameters: {} },
      { id: 'smoke-data', name: '테스트 초안', type: 'n8n-nodes-base.code', typeVersion: 2, position: [224, 0],
        parameters: { jsCode: `return [{ json: ${JSON.stringify(draft)} }];` } },
      ...ingestionNodes(),
    ],
    connections: { '수동 실행': { main: [[{ node: '테스트 초안', type: 'main', index: 0 }]] }, ...ingestionConnections('테스트 초안') },
  };
  importWorkflow(workflow);
  const first = execute();
  if (first.data.resultData.error) throw new Error(`First execution failed: ${first.data.resultData.error.message}`);
  const receipt = first.data.resultData.runData['저장 확인 후 URL 기록'][0].data.main[0][0].json;
  assert.equal(receipt.httpStatus, 202);
  assert.equal(receipt.status, 'PENDING');
  assert.equal(receipt.cardNewsId, null);
  console.log(`PASS actual n8n execution -> HTTP 202 -> PENDING: ${JSON.stringify(receipt)}`);
  const second = execute();
  if (second.data.resultData.error) throw new Error(`Repeat execution failed: ${second.data.resultData.error.message}`);
  const repeat = second.data.resultData.runData['저장 확인 후 URL 기록'][0].data.main[0][0].json;
  assert.equal(repeat.submissionId, receipt.submissionId);
  console.log(`PASS identical resend returns submission ${receipt.submissionId}`);

  const invalidAuth = structuredClone(workflow);
  const invalidRequest = invalidAuth.nodes.find(n => n.id === 'aiknow-server-import');
  invalidRequest.parameters.authentication = 'none';
  delete invalidRequest.credentials;
  importWorkflow(invalidAuth);
  const unauthorized = execute();
  assert.ok(unauthorized.data.resultData.error, 'Missing auth must fail');
  assert.match(JSON.stringify(unauthorized.data.resultData.error), /401/);
  assert.equal(unauthorized.data.resultData.runData['저장 확인 후 URL 기록'], undefined);
  console.log('PASS missing auth returns 401; history node does not run');

  const conflicting = structuredClone(workflow);
  conflicting.nodes.find(n => n.id === 'smoke-data').parameters.jsCode = `return [{ json: ${JSON.stringify({ ...draft, title: 'Changed test title' })} }];`;
  importWorkflow(conflicting);
  const conflict = execute();
  assert.ok(conflict.data.resultData.error, 'Different content at the same URL must fail');
  assert.match(JSON.stringify(conflict.data.resultData.error), /409/);
  assert.equal(conflict.data.resultData.runData['저장 확인 후 URL 기록'], undefined);
  console.log('PASS conflicting resend returns 409; history node does not run');
  importWorkflow(workflow);
  console.log(`Verification complete. Test workflow: ${workflow.id}; source URL: ${draft.sourceUrl}`);
} finally {
  fs.rmSync(temp, { recursive: true, force: true });
}
