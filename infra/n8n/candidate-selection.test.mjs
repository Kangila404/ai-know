import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const workflow=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url)));
const code=name=>workflow.nodes.find(n=>n.name===name).parameters.jsCode;
const controller=s=>new Function('$input',code('작업 결정'))({first:()=>({json:s})})[0].json;
function blockedDraft(s) {
  return new Function('$','$input',code('모델 호출 준비'))(()=>({itemMatching:()=>({json:s})}),
    {first:()=>({json:{statusCode:200,body:{decision:'BLOCKED',id:7,reason:'Existing reservation'}}})})[0].json;
}

test('blocked drafts advance to another bounded article without paying or deleting reservations',()=>{
  const candidates=[{sourceUrl:'https://example.test/old'},{sourceUrl:'https://example.test/new'}];
  const s={task:'draft',phase:'draft',current:candidates[0],candidateIndex:0,candidates,
    counters:{textCalls:1,candidates:1,transitions:2,documentCalls:1,paidTextCalls:0},audit:[]};
  const prepared=blockedDraft(s);
  assert.equal(prepared.cacheRoute,3);assert.equal(prepared.phase,'candidate');
  assert.equal(prepared.counters.paidTextCalls,0);assert.equal(prepared.cacheClaim.id,7);
  assert.equal(prepared.audit[0].event,'reserved_draft_skipped');
  assert.equal(workflow.connections['모델 캐시 처리 경로'].main[3][0].node,'작업 결정');
  const next=controller(prepared);
  assert.equal(next.current.sourceUrl,candidates[1].sourceUrl);assert.equal(next.task,'article');
  assert.equal(next.counters.textCalls,1);assert.equal(next.counters.paidTextCalls,0);
  assert.equal(next.candidates.length,2);
  const exhausted=blockedDraft({...next,task:'draft'});
  assert.equal(exhausted.cacheRoute,2);assert.equal(exhausted.status,'blocked');
});

test('image-selection reservations and missing candidate context still stop for recovery',()=>{
  for(const s of [{task:'image_selection',candidateIndex:0,candidates:[{},{}]}, {task:'draft'}]){
    const next=blockedDraft({...s,counters:{paidTextCalls:0},audit:[]});
    assert.equal(next.cacheRoute,2);assert.equal(next.phase,'stop');assert.equal(next.status,'blocked');
    assert.equal(next.counters.paidTextCalls,0);
  }
});
test('five saved newest articles cannot hide the sixth unprocessed article', () => {
  const initial=workflow.nodes.find(n=>n.name==='최근 기사와 중복 검사').parameters.jsCode;
  const filter=workflow.nodes.find(n=>n.name==='저장된 기사 제외').parameters.jsCode;
  const config={maxCandidates:5,maxArticleChars:10000};
  const rows=Array.from({length:9},(_,i)=>({json:{link:`https://techcrunch.com/news-${i}`,title:'기사 '+i,
    pubDate:new Date(Date.now()-(i+1)*60000).toUTCString(),content:'Source paragraph '.repeat(30)}}));
  const state=new Function('$','$input','$getWorkflowStaticData',initial)(()=>({first:()=>({json:{config,startedAt:'test'}})}),{all:()=>rows},()=>({}))[0].json;
  assert.equal(state.candidates.length,9);
  const saved=state.candidates.slice(0,5).map(c=>({sourceUrl:c.sourceUrl}));
  const result=new Function('$','$input',filter)(()=>({first:()=>({json:state})}),{first:()=>({json:{statusCode:200,body:saved}})})[0].json;
  assert.equal(result.candidates.length,4);
  assert.equal(result.candidates[0].sourceUrl,'https://techcrunch.com/news-5');
  assert.equal(result.audit[0].detail.scanned,9);
});
