import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { koreanDraftSchema, assertKoreanText, validateKoreanDraft } from './korean-draft.mjs';
const w=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
const code=w.nodes.find(n=>n.name==='작업 결정').parameters.jsCode;
const schema=JSON.parse(code.match(/const DRAFT_SCHEMA = ([^\n]+);/)[1]);

test('output schema constrains Korean copy and preserves English image fields',()=>{
  const s=koreanDraftSchema(schema),slide=s.properties.slides.items.properties;
  for(const field of [s.properties.title,s.properties.summary,slide.title,slide.content,slide.visual.properties.description]){
    assert.ok(new RegExp(field.pattern).test('Lambda의 AI 투자 소식입니다.'));
    assert.equal(new RegExp(field.pattern).test('Lambdaが資金調達へ'),false);
    assert.equal(new RegExp(field.pattern).test('기사에 따르면、資金を調達しました。'),false);
  }
  assert.ok(new RegExp(s.properties.title.pattern).test(''));
  assert.equal(new RegExp(slide.content.pattern).test(''),false);
  assert.deepEqual(slide.visual.properties.searchQuery,schema.properties.slides.items.properties.visual.properties.searchQuery);
  assert.deepEqual(slide.visual.properties.generationPrompt,schema.properties.slides.items.properties.visual.properties.generationPrompt);
});

test('runtime validation identifies mixed-language slide fields rather than accepting a Hangul fragment',()=>{
  assert.throws(()=>assertKoreanText('記事は、수요가 늘었다고 전했습니다.','슬라이드 4 본문'),/슬라이드 4 본문/);
  assert.doesNotThrow(()=>assertKoreanText('AI 모델 Lambda의 IPO 계획입니다.','본문'));
  assert.doesNotThrow(()=>validateKoreanDraft({status:'skip',reason:'충분한 근거가 없습니다.'}));
  assert.throws(()=>validateKoreanDraft({status:'skip',reason:'Not enough evidence'}),/사유/);
});
