import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {modelFailure,dispatchModelRetry} from './model-retry.mjs';
const workflow=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
const byId=id=>workflow.nodes.find(n=>n.id===id);
const state=()=>({config:{dryRun:false,maxModelInputChars:22000},task:'draft',phase:'draft',
  counters:{textCalls:1,transitions:0,candidates:1},cacheClaim:{id:11,decision:'REPLAY'},audit:[],usage:[],
  current:{sourceUrl:'https://example.test/news'},candidates:[],docs:[],
  request:{model:'test',input:[{role:'system',content:'Rules'},{role:'user',content:'Original source'}]}});

test('failed draft schedules one corrected request, then delegates to approved daily fallback',()=>{
  const s=state(), original=structuredClone(s.request);
  modelFailure(s,'Invalid evidence');
  assert.equal(s.phase,'model_retry');assert.equal(s.retryOf,11);assert.equal(s.retryUsed,true);
  assert.deepEqual(s.request,original);assert.equal(s.modelRetryRequest.input.length,3);
  assert.equal(dispatchModelRetry(s),true);assert.equal(s.route,1);assert.equal(s.counters.textCalls,2);
  modelFailure(s,'Still invalid');
  assert.equal(s.status,'fallback');assert.equal(s.phase,'stop');
  assert.equal(s.fallback.requiresApproval,true);assert.equal(dispatchModelRetry(s),false);
});

test('draft and image selection share the one retry budget; dry run and oversized inputs do not call',()=>{
  const s=state();modelFailure(s,'bad');dispatchModelRetry(s);delete s.retryOf;s.task='image_selection';
  s.cacheClaim={id:13};modelFailure(s,'bad selection');assert.equal(s.status,'fallback');
  for(const variant of [{dryRun:true},{maxModelInputChars:2}]){
    const s=state();Object.assign(s.config,variant);modelFailure(s,'bad');assert.equal(s.status,'fallback');assert.equal(s.modelRetryRequest,undefined);
  }
});

test('actual validator/controller/claim/terminal nodes route a retry and cannot loop a third time',()=>{
  const validation=byId('589b5b7f-84f9-4c54-977e-894a4f30a3f9').parameters.jsCode;
  const runValidator=s=>new Function('$input','$',validation)(
    {first:()=>({json:{statusCode:503,body:{error:{message:'Temporary failure'}}}})},
    ()=>({itemMatching:()=>({json:s})}))[0].json;
  const once=runValidator(state());assert.equal(once.phase,'model_retry');
  const dispatched=new Function('$input',byId('61913e57-2fcd-4cd7-a12b-344ffaeaa78f').parameters.jsCode)(
    {first:()=>({json:once})})[0].json;
  assert.equal(dispatched.route,1);assert.equal(dispatched.retryOf,11);
  const expr=byId('aiknow-generation-claim').parameters.jsonBody.slice(3,-2);
  const claim=JSON.parse(new Function('$json','return '+expr)(dispatched));
  assert.equal(claim.task,'text_retry');assert.equal(claim.retryOf,11);
  const twice=runValidator(dispatched);assert.equal(twice.status,'fallback');
  const end=new Function('$input',byId('5b9b87b7-88e3-4655-a227-5f85accf0281').parameters.jsCode)(
    {first:()=>({json:twice})})[0].json;
  assert.equal(end.status,'fallback');assert.equal(end.retryUsed,true);assert.equal(end.fallback.requiresApproval,true);
  assert.equal(end.processedUrlRecorded,false);
});

test('retry reservation conflict uses fallback instead of another candidate or paid call',()=>{
  const before={...state(),retryUsed:true,retryOf:11,candidateIndex:0,candidates:[{},{}]};
  const code=byId('aiknow-generation-prepare').parameters.jsCode;
  const result=new Function('$input','$',code)({first:()=>({json:{statusCode:200,body:{decision:'BLOCKED',id:12,reason:'Already attempted'}}})},
    ()=>({itemMatching:()=>({json:before})}))[0].json;
  assert.equal(result.cacheRoute,2);assert.equal(result.status,'fallback');
  assert.equal(result.counters.paidTextCalls,0);
});
