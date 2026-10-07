import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { numericValues, assertSupportedNumbers, parseVisualJson, normalizeImagePlan } from './draft-validation.mjs';

test('English money and Korean magnitude notation compare by value', () => {
  assertSupportedNumbers('시리즈 A 2,000만 달러, 시드 500만 달러, 총 2,500만 달러',
    '$25 million total, including $20 million Series A and $5 million seed', 'slide 1');
  assertSupportedNumbers('54억 달러', '$5.4 billion', 'valuation');
  assertSupportedNumbers('2천만 달러', '$20 million', 'investment');
  assertSupportedNumbers('1억 2천만 달러', '$120 million', 'investment');
  assertSupportedNumbers('100만 달러', '$1 million', 'revenue');
});

test('English written counts and named months support their translated digits', () => {
  assertSupportedNumbers('6개월 이후, 7월에 시작', 'more than six months, starting in July', 'slide 2');
  assert.deepEqual(numericValues('twenty-one months, thirty two people'), ['21', '32']);
  assert.deepEqual(numericValues('one hundred million'), ['100000000']);
});

test('different magnitudes, invented values and dates still fail', () => {
  assert.throws(() => assertSupportedNumbers('2,500만 달러', '$20 million', 'slide 1'), /25000000/);
  assert.throws(() => assertSupportedNumbers('20만 달러', '$20 million', 'slide 1'), /200000/);
  assert.throws(() => assertSupportedNumbers('7개월', 'six months', 'slide 2'), /7/);
  assert.throws(() => assertSupportedNumbers('8월', 'July', 'summary'), /8/);
  assert.throws(() => assertSupportedNumbers('매출 100만 달러', 'no revenue provided', 'summary'), /1000000/);
});

test('ordinary numbers, decimals, percentages and grouped digits remain checked', () => {
  assertSupportedNumbers('2026년, 1,200명, 3.5%', '2026, 1200, 3.5%', 'summary');
  assert.throws(() => assertSupportedNumbers('35%', '3.5%', 'summary'), /35/);
});

test('English singular measurements support translated digits without treating every article as one', () => {
  assertSupportedNumbers('첫 출시 뒤 거의 1년이 지났습니다.',
    'Nearly a year after its initial launch, it revealed a platform.', 'slide 4');
  assertSupportedNumbers('약 1개월, 1시간', 'about a month and an hour', 'slide');
  assertSupportedNumbers('100만 달러', 'a million dollars', 'slide');
  assert.deepEqual(numericValues('a platform, an engineer, a few years'), []);
  assert.deepEqual(numericValues('half a year, half an hour'), ['0.5', '0.5']);
  assert.throws(() => assertSupportedNumbers('2년', 'nearly a year', 'slide'), /2/);
  assert.throws(() => assertSupportedNumbers('1년', 'a few years', 'slide'), /1/);
  assert.throws(() => assertSupportedNumbers('1년', 'half a year', 'slide'), /1/);
});

test('decode at most one redundant JSON string layer without evaluating code', () => {
  const value = { columns: ['항목', '대상'], rows: [['이전', '광고비 관리']] };
  const json = JSON.stringify(value);
  assert.deepEqual(parseVisualJson(json), value);
  assert.deepEqual(parseVisualJson(JSON.stringify(json).slice(1, -1)), value);
  assert.throws(() => parseVisualJson('{ invalid json }'), /JSON 오류/);
  assert.throws(() => parseVisualJson('process.exit()'), /JSON 오류/);
});

test('nullable image search keywords derive from the asset key; valid explicit queries remain', () => {
  const plan = { assetKey: 'startup_team', searchQuery: null, generationPrompt: 'Generic team illustration' };
  assert.equal(normalizeImagePlan(plan, 1).searchQuery, 'startup team');
  const explicit = { ...plan, searchQuery: ' startup engineers ' };
  assert.equal(normalizeImagePlan(explicit, 2).searchQuery, 'startup engineers');
  assert.throws(() => normalizeImagePlan({ ...plan, assetKey: ['startup'] }, 3), /슬라이드 3.*assetKey/);
  assert.throws(() => normalizeImagePlan({ ...plan, generationPrompt: null }, 4), /슬라이드 4.*generationPrompt/);
  assert.throws(() => normalizeImagePlan({ ...plan, searchQuery: 'a'.repeat(181) }, 5), /슬라이드 5.*searchQuery/);
});

test('deployed workflow helpers accept translated quantities and still reject fabrication', () => {
  const workflow = JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json', import.meta.url), 'utf8'));
  const quote = 'The project raised $20 million after six months, starting in July.';
  const evidence = [{ sourceId: 'source-1', quote }];
  const state = { docs: [{ id: 'source-1', title: 'Example', text: quote }] };
  const draft = { status: 'ready', title: '투자 유치 소식', summary: '2,000만 달러를 유치했습니다.',
    plan: { coreMessage: '소식', readerNeeds: '배경', sequenceReason: '전개' }, evidence,
    slides: Array.from({ length: 3 }, (_, i) => ({ sequence: i + 1, title: '투자 소식',
      content: '2,000만 달러, 6개월, 7월', sourceIds: ['source-1'], evidence,
      visual: { type: 'text', description: '텍스트', dataJson: null, assetKey: null, searchQuery: null, generationPrompt: null } })) };
  const helpers = workflow.nodes.filter(node => node.parameters?.jsCode?.includes('function validateDraft'));
  assert.ok(helpers.length > 0);
  for (const node of helpers) {
    const code = node.parameters.jsCode;
    const validate = new Function(code.slice(0, code.indexOf('function requestModel')) + '\nreturn validateDraft;')();
    assert.equal(validate(structuredClone(draft), state).draft.slides.length, 3, node.name);
    const illustrated = structuredClone(draft);
    illustrated.slides[0].visual = { type: 'illustration', description: '추상적인 팀 이미지', dataJson: null,
      assetKey: 'startup_team', searchQuery: null, generationPrompt: 'Generic team illustration, no text' };
    assert.equal(validate(illustrated, state).assets[0].searchQuery, 'startup team', node.name);
    illustrated.slides[1].visual = { ...illustrated.slides[0].visual, description: '같은 그림을 다른 설명과 함께 재사용' };
    const reused = validate(structuredClone(illustrated), state);
    assert.equal(reused.assets.length, 1, node.name);
    assert.equal(reused.assets[0].description, illustrated.slides[0].visual.description, node.name);
    assert.equal(reused.draft.slides[1].visual.description, illustrated.slides[1].visual.description, node.name);
    illustrated.slides[1].visual.generationPrompt = 'A completely different scene';
    assert.throws(() => validate(illustrated, state), /슬라이드 2.*generationPrompt/, node.name);
    const fabricated = structuredClone(draft);
    fabricated.slides[0].content = '9,999만 달러를 유치했습니다.';
    assert.throws(() => validate(fabricated, state), /99990000/, node.name);
  }
});
