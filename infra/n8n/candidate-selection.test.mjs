import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const workflow=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url)));
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
