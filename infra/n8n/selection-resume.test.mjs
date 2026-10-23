import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {generationNodes} from './generation-cache-nodes.mjs';
import {selectionRecovery} from './selection-recovery.mjs';
const workflow=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
const code=name=>workflow.nodes.find(n=>n.name===name).parameters.jsCode;
const controller=s=>new Function('$input',code('작업 결정'))({first:()=>({json:s})})[0].json;
const state=()=>({phase:'search',draft:{title:'Original draft'},assets:[{key:'music',searchQuery:'headphones',candidates:[]}],
  assetIndex:0,counters:{transitions:0,textCalls:1,commonsCalls:0},audit:[]});
function restore(s,body) {
  return new Function('$input','$',code('기존 이미지 선택 복원'))({first:()=>({json:{statusCode:200,body}})},
    ()=>({itemMatching:()=>({json:s})}))[0].json;
}
test('lookup precedes external image searches; a new article can then search normally',()=>{
  let s=controller(state());assert.equal(s.route,6);assert.equal(s.counters.imageSearchCalls,undefined);
  s=controller(restore(s,{decision:'NONE'}));assert.equal(s.route,2);assert.equal(s.counters.imageSearchCalls,1);
});
test('restore original candidates and exact request, bypass all external searches and reuse model cache',()=>{
  const request={model:'original-model',input:[{role:'user',content:'old selection'}]};
  const original=[{key:'music',candidates:[{candidateId:'original',url:'https://example.test/old.jpg'}]}];
  let s=restore(state(),{decision:'RESTORE',id:4,requestJson:JSON.stringify(request),contextJson:JSON.stringify({version:1,draft:state().draft,assets:original})});
  s=controller(s);assert.equal(s.route,1);assert.equal(s.task,'image_selection');assert.deepEqual(s.request,request);
  assert.deepEqual(s.assets,original);assert.equal(s.counters.imageSearchCalls,undefined);
  const prepared=new Function('$input','$',code('모델 호출 준비'))({first:()=>({json:{statusCode:200,body:{decision:'REPLAY',id:4,responseJson:'{}',contextJson:JSON.stringify({version:1,draft:state().draft,assets:original})}}})},
    ()=>({itemMatching:()=>({json:s})}))[0].json;
  assert.equal(prepared.cacheRoute,1);assert.equal(prepared.counters.paidTextCalls,0);
});
test('legacy/missing responses block before searches or new paid requests',()=>{
  const s=controller(restore(state(),{decision:'BLOCKED',id:4,reason:'Recover original context'}));
  assert.equal(s.route,5);assert.equal(s.status,'blocked');assert.equal(s.cacheClaim.id,4);assert.equal(s.counters.imageSearchCalls,undefined);
});
test('claim stores only draft/assets snapshot, not config secrets or whole execution',()=>{
  const expr=generationNodes('http://server',{}).find(n=>n.id==='aiknow-generation-claim').parameters.jsonBody;
  const s={...state(),task:'image_selection',current:{sourceUrl:'https://example.test'},request:{input:[]},config:{secret:'never-store'}};
  const body=JSON.parse(new Function('$json','return '+expr.slice(3,-3))(s));
  assert.deepEqual(Object.keys(JSON.parse(body.contextJson)).sort(),['assets','draft','version']);
  s.task='draft';assert.equal(JSON.parse(new Function('$json','return '+expr.slice(3,-3))(s)).contextJson,undefined);
});
test('legacy recovery ignores changed-input BLOCKED execution and picks original record only',()=>{
  const run=(decision,id,request)=>({data:{main:[[{json:{...state(),task:'image_selection',cacheClaim:{decision,id},request}}]]}});
  assert.equal(selectionRecovery({'모델 호출 준비':[run('BLOCKED',4,{wrong:true})]},4),null);
  const recovered=selectionRecovery({'모델 호출 준비':[run('CALL',3,{}),run('CALL',4,{original:true}),run('BLOCKED',4,{wrong:true})]},4);
  assert.deepEqual(JSON.parse(recovered.requestJson),{original:true});assert.equal(recovered.id,4);
});
test('generated nodes match source and searches retain a bounded 60-second timeout',()=>{
  for(const n of generationNodes('http://server',{})) {
    const live=workflow.nodes.find(x=>x.id===n.id);assert.ok(live,n.id);
    if(n.parameters.jsCode)assert.equal(live.parameters.jsCode,n.parameters.jsCode);
  }
  assert.equal(workflow.nodes.find(n=>n.name==='관련 이미지 검색').parameters.options.timeout,60000);
});
