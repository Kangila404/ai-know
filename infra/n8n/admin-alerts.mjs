import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

export const errorWorkflowId = 'aiknow-automation-errors';
const endpoint = '/internal/v1/automation/events';
export function alertRequest(id, name, position) {
  return { id, name, position, type: 'n8n-nodes-base.httpRequest', typeVersion: 4.2,
    retryOnFail: true, maxTries: 3, waitBetweenTries: 2000,
    parameters: { method: 'POST', url: 'http://host.docker.internal:8080'+endpoint,
      authentication: 'genericCredentialType', genericAuthType: 'httpHeaderAuth',
      sendBody: true, specifyBody: 'json', jsonBody: '={{ JSON.stringify($json.alertEvent) }}',
      options: { timeout: 15000, redirect: { redirect: { followRedirects: false } } } },
    credentials: { httpHeaderAuth: { id: 'aiknow-local-ingest', name: 'AI_KNOW Ingestion' } } };
}
export const errorEventCode = `const input=$input.first().json;
const workflowId=String(input.workflow?.id||'');
const executionId=String(input.execution?.id||('trigger-'+$execution.id));
if(!/^[A-Za-z0-9_-]{1,64}$/.test(workflowId)||!/^[A-Za-z0-9_-]{1,64}$/.test(executionId)) throw new Error('Invalid automation event IDs');
return [{json:{alertEvent:{workflowId,executionId,status:'FAILED',stage:String(input.execution?.lastNodeExecuted||'자동 실행 시작').slice(0,80)}}}];`;

export function errorWorkflow() {
  return { id: errorWorkflowId, name: 'AI_KNOW — 자동화 오류 알림', active: false,
    settings: { executionOrder: 'v1', timezone: 'Asia/Seoul' }, nodes: [
      { id:'aiknow-error-trigger', name:'자동화 오류', type:'n8n-nodes-base.errorTrigger', typeVersion:1, position:[0,0], parameters:{} },
      { id:'aiknow-error-event', name:'오류 알림 준비', type:'n8n-nodes-base.code', typeVersion:2, position:[240,0], parameters:{jsCode:errorEventCode} },
      alertRequest('aiknow-automation-event', '관리자 오류 알림 저장', [480,0])
    ], connections: {
      '자동화 오류': { main: [[{node:'오류 알림 준비',type:'main',index:0}]] },
      '오류 알림 준비': { main: [[{node:'관리자 오류 알림 저장',type:'main',index:0}]] }
    } };
}

export function withAdminAlerts(source) {
  const wf=structuredClone(source);
  wf.settings={...wf.settings,errorWorkflow:errorWorkflowId};
  const terminal=wf.nodes.find(n=>n.name==='점검 또는 정상 종료');
  if(!terminal) throw new Error('Missing terminal node');
  const filter={id:'aiknow-no-content-event',name:'미생성 알림 준비',type:'n8n-nodes-base.code',typeVersion:2,position:[1400,2000],
    parameters:{jsCode:`const result=$input.first().json;
if(result.status==='preview') return [];
return [{json:{alertEvent:{workflowId:String($workflow.id),executionId:String($execution.id),status:'NO_CONTENT',stage:'점검 또는 정상 종료'}}}];`}};
  const request=alertRequest('aiknow-automation-event','관리자 미생성 알림 저장',[1640,2000]);
  for(const node of [filter,request]) { const i=wf.nodes.findIndex(n=>n.id===node.id); if(i<0) wf.nodes.push(node); else wf.nodes[i]=node; }
  wf.connections[terminal.name]={main:[[{node:filter.name,type:'main',index:0}]]};
  wf.connections[filter.name]={main:[[{node:request.name,type:'main',index:0}]]};
  return wf;
}

if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  const main=new URL('./workflows/ai-know-news-draft.json',import.meta.url);
  fs.writeFileSync(main,JSON.stringify(withAdminAlerts(JSON.parse(fs.readFileSync(main,'utf8'))),null,2)+'\n');
  fs.writeFileSync(new URL('./workflows/ai-know-automation-errors.json',import.meta.url),JSON.stringify(errorWorkflow(),null,2)+'\n');
}
