// Run offline via the n8n-workflows container. Uses an existing ingestion credential.
// Uploads a tiny test PNG and creates one labelled PENDING draft; never calls OpenAI.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { imageUploadNodes, imageUploadConnections } from './image-upload-nodes.mjs';
import { ingestionNodes, ingestionConnections } from './ingestion-nodes.mjs';
import { readWorkflowEnvironment } from './workflow-environment.mjs';

const base = process.argv[2];
if (!base || !/^https?:\/\//.test(base)) throw new Error('Pass the reachable Spring base URL');
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-upload-check-'));
function cli(args) {
  const r = spawnSync('n8n', args, { encoding: 'utf8', timeout: 180000, maxBuffer: 16 * 1024 * 1024 });
  if (r.error || r.status !== 0) throw new Error(`n8n ${args[0]} failed: ${r.stderr.slice(-1000)}`);
  return r.stdout;
}
try {
  let credentials;
  if (!process.argv.includes('--isolated')) {
    const exported = path.join(dir, 'main.json');
    cli(['export:workflow', '--id=iwGOpyXhwAq9tON2', `--output=${exported}`]);
    const [main] = JSON.parse(fs.readFileSync(exported, 'utf8'));
    credentials = main.nodes.find(n => n.id === 'aiknow-server-import').credentials;
  }
  // Optional stdin is for an isolated test server; never replaces production credentials.
  if (process.argv.includes('--isolated')) {
    const { token } = JSON.parse(fs.readFileSync(0, 'utf8'));
    if (typeof token !== 'string' || token.length < 32) throw new Error('Test token required');
    const credential = { id: 'aiknow-image-smoke-only', name: 'AI_KNOW isolated image test', type: 'httpHeaderAuth',
      data: { name: 'Authorization', value: 'Bearer ' + token } };
    const credentialFile = path.join(dir, 'credential.json');
    fs.writeFileSync(credentialFile, JSON.stringify([credential]), { mode: 0o600 });
    cli(['import:credentials', '--input=' + credentialFile]);
    credentials = { httpHeaderAuth: { id: credential.id, name: credential.name } };
  }
  const draft = { sourceTitle: '[LOCAL STORAGE TEST]', sourceUrl: `https://example.com/aiknow/storage-test/${Date.now()}`,
    publishedAt: new Date().toISOString(), title: '[로컬 테스트] 생성 이미지 저장 연결 확인', summary: '실제 뉴스가 아닌 저장소 검증용 초안입니다. 승인하지 마세요.',
    slides: [1, 2].map(sequence => ({ sequence, title: '저장소 연결 확인', content: '테스트 PNG를 공유하는 슬라이드입니다.',
      visual: { type: 'illustration', image: { origin: 'generated', url: null, binaryProperty: 'test_png' } } })) };
  const media = readWorkflowEnvironment(process.env)?.media ?? {};
  const nodes = imageUploadNodes(base, credentials, media);
  const imports = ingestionNodes(base + '/internal/v1/card-news/import', media);
  imports.find(n => n.id === 'aiknow-server-import').credentials = credentials;
  const code = `const bytes=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=','base64');
return [{json:${JSON.stringify(draft)},binary:{test_png:await this.helpers.prepareBinaryData(bytes,'test.png','image/png')}}];`;
  const links = imageUploadConnections();
  // The smoke workflow has no schedule, RSS or paid API nodes.
  for (const outputs of Object.values(links)) for (const group of outputs.main) for (const edge of group)
    if (edge.node === 'TechCrunch AI RSS') edge.node = '초안 검증 완료';
  const wf = { id: 'aiknow-image-storage-smoke', name: 'AI_KNOW [LOCAL TEST] 이미지 업로드 확인', active: false,
    settings: { executionOrder: 'v1' }, nodes: [
      { id: 'manual', name: '수동 실행', type: 'n8n-nodes-base.manualTrigger', typeVersion: 1, position: [0, 0], parameters: {} },
      { id: 'config', name: '실행 설정', type: 'n8n-nodes-base.code', typeVersion: 2, position: [100, 0],
        parameters: { jsCode: 'return [{json:{config:{dryRun:false,allowImageGeneration:true}}}];' } },
      { id: 'fixture', name: '초안 검증 완료', type: 'n8n-nodes-base.code', typeVersion: 2, position: [200, 0], parameters: { jsCode: code } },
      ...nodes, ...imports,
    ], connections: { '수동 실행': { main: [[{ node: '실행 설정', type: 'main', index: 0 }]] },
      ...ingestionConnections('이미지 URL 반영'), ...links } };
  const file = path.join(dir, 'test.json'); fs.writeFileSync(file, JSON.stringify([wf]));
  cli(['import:workflow', `--input=${file}`, '--activeState=false']);
  const output = cli(['execute', '--id=' + wf.id, '--rawOutput']);
  const result = (output.match(/^\{[\s\S]*?^\}/gm) ?? []).map(x => { try { return JSON.parse(x); } catch { return null; } })
    .find(x => x?.data?.resultData)?.data.resultData;
  assert.ok(result, 'Missing execution result');
  assert.ok(!result.error, result.error?.message);
  const receipt = result.runData['저장 확인 후 URL 기록'][0].data.main[0][0].json;
  const request = result.runData['서버 요청 변환'][0].data.main[0][0].json.request;
  assert.equal(receipt.httpStatus, 202); assert.equal(receipt.status, 'PENDING');
  assert.equal(request.slides[0].image.url, request.slides[1].image.url);
  const key = request.slides[0].image.url.split('/').at(-1);
  const read = await fetch(base + '/media/generated/' + key);
  assert.equal(read.status, 200); assert.equal(read.headers.get('content-type'), 'image/png');
  assert.ok((await read.arrayBuffer()).byteLength > 0);
  console.log(JSON.stringify({ receipt, imageUrl: request.slides[0].image.url, imageHttpStatus: read.status,
    paidApiCalls: 0, uniqueUploads: result.runData['생성 이미지 서버 업로드'][0].data.main[0].length }));
} finally { fs.rmSync(dir, { recursive: true, force: true }); }
