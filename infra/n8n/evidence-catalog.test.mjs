import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { evidenceCatalog, evidenceRequest, resolveEvidence } from './evidence-catalog.mjs';

const docs = [{id:'source-1',title:'A sufficiently long title',text:'He acknowledged that the company is growing. Nearly a year after launch, a new platform was revealed.'}];
test('evidence IDs resolve to exact source bytes and the schema limits IDs', () => {
  const state={docs};
  const w=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
  const code=w.nodes.find(n=>n.name==='작업 결정').parameters.jsCode;
  const schema=JSON.parse(code.match(/const DRAFT_SCHEMA = ([^\n]+);/)[1]);
  const r=evidenceRequest(state,schema);
  assert.equal(r.sources[0].text,undefined);
  const selected=state.evidenceCatalog.find(q=>q.quote.startsWith('He acknowledged'));
  assert.ok(r.schema.properties.evidence.items.enum.includes(selected.id));
  const draft={evidence:[selected.id],slides:[{sequence:1,evidence:[selected.id]}]};
  resolveEvidence(draft,state.evidenceCatalog);
  assert.equal(draft.evidence[0].quote,selected.quote);
  assert.equal(draft.slides[0].evidence[0].sourceId,'source-1');
  assert.throws(()=>resolveEvidence({evidence:['made-up'],slides:[]},state.evidenceCatalog),/없는 근거 ID/);
});

test('catalog-derived quotes pass the actual deployed validator; invented claims still fail',()=>{
  const w=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
  const state={docs,evidenceCatalog:evidenceCatalog(docs)};
  const ref=state.evidenceCatalog.find(q=>q.quote.startsWith('Nearly')).id;
  const d={status:'ready',title:'새 플랫폼 공개',summary:'거의 1년 후 새 플랫폼을 공개했습니다.',
    plan:{coreMessage:'소식',readerNeeds:'배경',sequenceReason:'전개'},evidence:[ref],
    slides:Array.from({length:3},(_,i)=>({sequence:i+1,title:'플랫폼 소식',content:'거의 1년이 지났습니다.',sourceIds:['source-1'],evidence:[ref],
      visual:{type:'text',description:'본문',dataJson:null,assetKey:null,searchQuery:null,generationPrompt:null}}))};
  for(const n of w.nodes.filter(n=>n.parameters?.jsCode?.includes('function validateDraft'))){
    const c=n.parameters.jsCode;const validate=new Function(c.slice(0,c.indexOf('function requestModel'))+'\nreturn validateDraft;')();
    assert.equal(validate(structuredClone(d),state).draft.slides.length,3);
    const invented=structuredClone(d);invented.slides[0].content='99년이 지났습니다.';
    assert.throws(()=>validate(invented,state),/99/);
    const rewritten=structuredClone(d);rewritten.evidence=[{sourceId:'source-1',quote:'Kim acknowledged that the company is growing.'}];
    assert.throws(()=>validate(rewritten,state),/인용문이 원문과 다릅니다/);
  }
});
