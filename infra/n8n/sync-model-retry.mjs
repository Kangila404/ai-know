import fs from 'node:fs';
import {modelFailure, dispatchModelRetry} from './model-retry.mjs';
import {generationNodes} from './generation-cache-nodes.mjs';
const file = new URL('./workflows/ai-know-news-draft.json',import.meta.url);
const w = JSON.parse(fs.readFileSync(file,'utf8'));
const node = id => w.nodes.find(n=>n.id===id);
function embed(n, marker, helper) {
  const begin='// BEGIN '+marker, end='// END '+marker;
  const block=begin+'\n'+helper+'\n'+end+'\n';
  const code=n.parameters.jsCode;
  const start=code.indexOf(begin), stop=code.indexOf(end,start);
  n.parameters.jsCode=start<0?block+code:code.slice(0,start)+block+code.slice(stop+end.length+1);
}
const controller=node('61913e57-2fcd-4cd7-a12b-344ffaeaa78f');
embed(controller,'MODEL RETRY DISPATCH',dispatchModelRetry.toString());
if(!controller.parameters.jsCode.includes('if (dispatchModelRetry(s))'))
  controller.parameters.jsCode=controller.parameters.jsCode.replace("delete s.request; delete s.url; delete s.task; delete s.route;",
    "delete s.request; delete s.url; delete s.task; delete s.route;\nif (dispatchModelRetry(s)) return [{json:s,...(item.binary?{binary:item.binary}:{}),pairedItem:{item:0}}];");
const validator=node('589b5b7f-84f9-4c54-977e-894a4f30a3f9');
embed(validator,'BOUNDED MODEL RETRY',modelFailure.toString());
validator.parameters.jsCode=validator.parameters.jsCode
  .replace('// Errors stop the run: no automatic paid retry, including timeouts and 429s.', '// One server-reserved retry; original responses remain intact.')
  .replace("fail(s,'텍스트 API 오류. 자동 재시도하지 않습니다: '","modelFailure(s,'텍스트 API 오류: '")
  .replace("fail(s,'모델 출력 검증 실패: '+e.message)","modelFailure(s,'모델 출력 검증 실패: '+e.message)");
if(!validator.parameters.jsCode.includes('delete s.retryOf;'))
  validator.parameters.jsCode=validator.parameters.jsCode.replace("    } else throw new Error('알 수 없는 모델 작업');",
    "    } else throw new Error('알 수 없는 모델 작업');\n    delete s.retryOf;");
// Refresh only changed cache nodes; preserve URLs, credentials and node positions.
const claim=node('aiknow-generation-claim');
const base=claim.parameters.url.replace('/internal/v1/generation/claim','');
const generated=generationNodes(base,claim.credentials);
for(const id of ['aiknow-generation-claim','aiknow-generation-prepare'])
  node(id).parameters=generated.find(n=>n.id===id).parameters;
const terminal=node('5b9b87b7-88e3-4655-a227-5f85accf0281');
if(!terminal.parameters.jsCode.includes('fallback:s.fallback'))
  terminal.parameters.jsCode=terminal.parameters.jsCode.replace("status:s.status||'failed',","status:s.status||'failed',fallback:s.fallback??null,retryUsed:s.retryUsed??false,");
const repeated="generationRecordId:s.imageClaim?.id||s.cacheClaim?.id||null,sourceUrl:s.current?.sourceUrl||null,";
while(terminal.parameters.jsCode.includes(repeated+repeated))
  terminal.parameters.jsCode=terminal.parameters.jsCode.replace(repeated+repeated,repeated);
for(const n of w.nodes) if(n.parameters?.jsCode)
  n.parameters.jsCode=n.parameters.jsCode.replaceAll('s.counters.textCalls>6','s.counters.textCalls>7');
fs.writeFileSync(file,JSON.stringify(w,null,2)+'\n');
console.log('One bounded model retry and server daily-edition fallback connected');
