import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
import { errorWorkflow, errorEventCode, withAdminAlerts } from './admin-alerts.mjs';
import { bindIngestionCredential, ingestionCredential } from './ingestion-credential.mjs';
const run=(code,input)=>vm.runInNewContext(`(function(){${code}})()`,{
  $input:{first:()=>({json:input})},$execution:{id:'321'},$workflow:{id:'main'}
});
test('error callback contains only operational metadata, even for trigger failures',()=>{
  const output=run(errorEventCode,{workflow:{id:'news'},execution:{id:'123',lastNodeExecuted:'서버 요청 변환',error:{message:'secret'}}});
  assert.equal(output[0].json.alertEvent.executionId,'123');
  assert.ok(!JSON.stringify(output).includes('secret'));
  assert.equal(run(errorEventCode,{workflow:{id:'news'},trigger:{error:{message:'secret'}}})[0].json.alertEvent.executionId,'trigger-321');
});
test('preview sends no admin alert and no-content reports stable parent execution IDs',()=>{
  const source=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url)));
  const wf=withAdminAlerts(source);
  assert.deepEqual(withAdminAlerts(wf),wf);
  const code=wf.nodes.find(n=>n.id==='aiknow-no-content-event').parameters.jsCode;
  assert.equal(run(code,{status:'preview'}).length,0);
  assert.equal(run(code,{status:'fallback'})[0].json.alertEvent.status,'NO_CONTENT');
  assert.equal(wf.settings.errorWorkflow,errorWorkflow().id);
});
test('error callback uses restricted backend credentials, no redirects and bounded retries',()=>{
  const wf=errorWorkflow(), node=wf.nodes.find(n=>n.id==='aiknow-automation-event');
  const credential=ingestionCredential(wf.id,'test-token-'.repeat(4));
  node.parameters.url='http://server:8080/internal/v1/automation/events';
  assert.equal(bindIngestionCredential(wf,'http://server:8080',credential).nodes.at(-1).credentials.httpHeaderAuth.id,credential.id);
  assert.equal(node.maxTries,3); assert.equal(wf.settings.errorWorkflow,undefined);
  node.parameters.options.redirect.redirect.followRedirects=true;
  assert.throws(()=>bindIngestionCredential(wf,'http://server:8080',credential),/settings differ/);
});
