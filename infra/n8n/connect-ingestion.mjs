import fs from 'node:fs';
import { ingestionNodes, ingestionConnections } from './ingestion-nodes.mjs';
import { imageUploadNodes, imageUploadConnections } from './image-upload-nodes.mjs';
import { generationNodes, generationConnections } from './generation-cache-nodes.mjs';
import { imageCacheNodes, imageCacheConnections } from './image-cache-nodes.mjs';

const file = new URL('./workflows/ai-know-news-draft.json', import.meta.url);
const wf = JSON.parse(fs.readFileSync(file, 'utf8'));
const oldName = '검증 완료와 URL 기록';
const newName = '초안 검증 완료';
const final = wf.nodes.find(n => n.name === oldName || n.name === newName);
if (!final) throw new Error('Expected draft validation node');
final.parameters.jsCode = final.parameters.jsCode.replace(
  '생성 이미지는 이 실행의 Binary 데이터입니다. 영구 URL은 아직 없습니다. 외부 저장을 붙일 때 저장 성공 뒤에 이력 기록을 옮기세요.',
  '생성 이미지는 다음 업로드 단계에서 영구 URL로 변환합니다. 서버 저장 완료 후에만 처리 이력을 기록합니다.');
if (final.name === oldName) {
  const markerAt = final.parameters.jsCode.indexOf('// Keep this commit at the very end.');
  const returnAt = final.parameters.jsCode.indexOf('return [{json:output', markerAt);
  if (markerAt < 0 || returnAt < 0) throw new Error('Cannot locate original history block');
  final.parameters.jsCode = final.parameters.jsCode.slice(0, markerAt)
    + '// Processing history is committed only after the server accepts the draft.\n'
    + final.parameters.jsCode.slice(returnAt);
  final.parameters.jsCode = final.parameters.jsCode.replace("'on_successful_published_trigger'", "'pending_server_acceptance'");
  final.name = newName;
  for (const outputs of Object.values(wf.connections)) {
    for (const groups of Object.values(outputs)) for (const group of groups) for (const edge of group) {
      if (edge.node === oldName) edge.node = newName;
    }
  }
  const note = wf.nodes.find(n => n.type === 'n8n-nodes-base.stickyNote');
  note.parameters.content += '\n\n## 서버 수집 연결\n초안 검증 → 서버 요청 변환 → 서버 검수 대기 저장 → 저장 확인 후 URL 기록.\nAI_KNOW Ingestion Header Auth Credential 사용. HTTP 202 성공 후에만 URL 처리 이력을 기록합니다.\n생성 이미지 Binary는 영구 HTTPS 저장 후 전송해야 합니다. 저장 전에는 오류로 중단합니다.\n검수 대기 PENDING으로 저장하며 자동 승인/게시/푸시는 하지 않습니다.';
}
const currentImport = wf.nodes.find(n => n.id === 'aiknow-server-import');
const baseUrl = (process.env.AIKNOW_SERVER_BASE_URL
  ?? currentImport?.parameters.url?.replace(/\/internal\/v1\/card-news\/import$/, '')
  ?? 'http://host.docker.internal:8080').replace(/\/$/, '');
const credentials = currentImport?.credentials ?? { httpHeaderAuth: { id: 'aiknow-local-ingest', name: 'AI_KNOW Ingestion' } };
for (const node of [...ingestionNodes(baseUrl + '/internal/v1/card-news/import'), ...imageUploadNodes(baseUrl, credentials), ...generationNodes(baseUrl, credentials), ...imageCacheNodes(baseUrl, credentials)]) {
  if (node.id === 'aiknow-server-import') node.credentials = credentials;
  const importIndex = ['aiknow-import-transform', 'aiknow-server-import', 'aiknow-import-receipt'].indexOf(node.id);
  if (importIndex >= 0) node.position = [3392 + importIndex * 224, 688];
  const index = wf.nodes.findIndex(existing => existing.id === node.id);
  if (index < 0) wf.nodes.push(node);
  else wf.nodes[index] = node;
}
Object.assign(wf.connections, ingestionConnections(newName));
Object.assign(wf.connections, imageUploadConnections());
Object.assign(wf.connections, generationConnections());
Object.assign(wf.connections, imageCacheConnections());
wf.connections['처리 경로'].main[3] = [{node:'이미지 호출 예약 또는 캐시 조회',type:'main',index:0}];
const acquired=wf.nodes.find(n=>n.name==='생성 이미지 확보');
acquired.parameters.jsCode=acquired.parameters.jsCode.replace("const before=$('작업 결정').itemMatching(0)","const before=$('이미지 호출 준비').itemMatching(0)");
final.parameters.jsCode=final.parameters.jsCode.replace("if(!item.binary?.[image.binaryProperty])", "if(!item.binary?.[image.binaryProperty]&&!image.storageKey)");
wf.connections['처리 경로'].main[1] = [{node:'모델 호출 예약 또는 캐시 조회',type:'main',index:0}];
const responseNode = wf.nodes.find(n=>n.name==='텍스트 응답 검증');
responseNode.parameters.jsCode=responseNode.parameters.jsCode.replace("const before=$('작업 결정').itemMatching(0)","const before=$('모델 호출 준비').itemMatching(0)");
const terminal=wf.nodes.find(n=>n.name==='점검 또는 정상 종료');
terminal.parameters.jsCode=terminal.parameters.jsCode.replace('candidatesReviewed:s.counters.candidates,', 'generationRecordId:s.cacheClaim?.id||null,sourceUrl:s.current?.sourceUrl||null,candidatesReviewed:s.counters.candidates,');
terminal.parameters.jsCode=terminal.parameters.jsCode.replace('generationRecordId:s.cacheClaim?.id||null,sourceUrl:s.current?.sourceUrl||null,generationRecordId:s.cacheClaim?.id||null,sourceUrl:s.current?.sourceUrl||null,', 'generationRecordId:s.cacheClaim?.id||null,sourceUrl:s.current?.sourceUrl||null,');
terminal.parameters.jsCode=terminal.parameters.jsCode.replace('generationRecordId:s.cacheClaim?.id||null,', 'generationRecordId:s.imageClaim?.id||s.cacheClaim?.id||null,');
fs.writeFileSync(file, JSON.stringify(wf, null, 2) + '\n');
console.log('Server import nodes connected. Schedule and dryRun settings preserved.');
