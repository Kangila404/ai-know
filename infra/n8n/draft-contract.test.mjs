import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {draftLimits,assertEvidence} from './draft-contract.mjs';
import {evidenceCatalog,evidenceRequest} from './evidence-catalog.mjs';
const w=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
const code=w.nodes.find(n=>n.name==='작업 결정').parameters.jsCode;
const schema=JSON.parse(code.match(/const DRAFT_SCHEMA = ([^\n]+);/)[1]);
const docs=Array.from({length:2},(_,i)=>({id:'source-'+(i+1),title:'Research update from the project team',
  text:Array.from({length:110},(_,j)=>`The project team released an update for partner ${String.fromCharCode(65+(j%26))} in the research network.`).join(' ')}));
const state={docs,evidenceCatalog:evidenceCatalog(docs)};
const sample=count=>({status:'ready',reason:'',title:'프로젝트 업데이트 소식',summary:'프로젝트 팀이 업데이트를 공개했습니다.',
  plan:{coreMessage:'업데이트 소식',readerNeeds:'배경 설명',sequenceReason:'주요 내용 순서'},
  evidence:state.evidenceCatalog.slice(0,count).map(q=>q.id),
  slides:Array.from({length:5},(_,i)=>({sequence:i+1,title:'업데이트 소식',content:'프로젝트 팀이 업데이트를 공개했습니다.',
    evidence:[state.evidenceCatalog[i].id],sourceIds:[state.evidenceCatalog[i].sourceId],
    visual:{type:'text',description:'핵심 내용을 텍스트로 설명합니다.',dataJson:null,assetKey:null,searchQuery:null,generationPrompt:null}}))});

test('long multi-source drafts with 9, 20 or 200 valid evidence IDs pass every embedded validator',()=>{
  for(const n of w.nodes.filter(n=>n.parameters?.jsCode?.includes('function validateDraft'))){
    const c=n.parameters.jsCode,validate=new Function(c.slice(0,c.indexOf('function requestModel'))+';return validateDraft;')();
    for(const count of [1,8,9,20,200])assert.equal(validate(sample(count),structuredClone(state)).draft.evidence.length,count,n.name);
    const extra=sample(200);extra.evidence.push(extra.evidence[0]);assert.throws(()=>validate(extra,structuredClone(state)),/근거 개수 상한 초과/);
  }
});

test('errors distinguish empty evidence, excessive count, unknown source and altered quote',()=>{
  assert.throws(()=>assertEvidence([],docs,null,'제목/요약'),/비어/);
  assert.throws(()=>assertEvidence([{sourceId:'absent',quote:'some sufficiently long quote'}],docs,null,'슬라이드 2'),/존재하지 않는 출처/);
  assert.throws(()=>assertEvidence([{sourceId:'source-1',quote:'This sentence is fabricated, unlike the source.'}],docs,null,'제목/요약'),/인용문이 원문과 다릅니다/);
  const quote=state.evidenceCatalog[0];assert.throws(()=>assertEvidence([quote],docs,['source-2'],'슬라이드 2'),/출처 목록/);
});

test('schema exposes runtime evidence, slide, text and image-plan limits before generation',()=>{
  const s=evidenceRequest(structuredClone(state),schema).schema,l=draftLimits(),p=s.properties;
  assert.equal(p.evidence.maxItems,l.evidence);assert.equal(p.evidence.minItems,0);
  assert.equal(p.slides.items.properties.evidence.maxItems,l.evidence);assert.equal(p.slides.items.properties.evidence.minItems,1);
  assert.equal(p.slides.maxItems,l.maxSlides);assert.equal(p.title.maxLength,l.title);assert.equal(p.summary.maxLength,l.summary);
  assert.equal(p.slides.items.properties.title.maxLength,l.slideTitle);assert.equal(p.slides.items.properties.content.maxLength,l.slideContent);
});
