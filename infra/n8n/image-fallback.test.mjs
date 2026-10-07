import test from 'node:test';
import assert from 'node:assert/strict';
import { fallbackImageToText } from './image-fallback.mjs';
import { toImportRequest, ingestionNodes } from './ingestion-nodes.mjs';

function fixture() {
  const visual = { type: 'illustration', description: 'original concept', assetKey: 'missing',
    data: null, dataJson: null, searchQuery: 'concept', generationPrompt: 'draw a concept' };
  return { draft: { sourceTitle: 'Original', sourceUrl: 'https://example.com/article',
    publishedAt: '2026-10-07T00:00:00Z', title: '제목', summary: '요약',
    slides: [1, 2].map(sequence => ({ sequence, title: '장 제목', content: '근거 있는 본문',
      sourceIds: ['source-1'], evidence: [{ sourceId: 'source-1', quote: 'Original evidence' }], visual: { ...visual } })) },
    audit: [] };
}

test('all slides sharing an unavailable image become text with their evidence preserved', () => {
  const state = fixture();
  const original = structuredClone(state.draft.slides);
  fallbackImageToText(state, { key: 'missing', image: null }, 'No licensed image');
  assert.deepEqual(state.visualWarnings[0].slides, [1, 2]);
  assert.equal(state.audit[0].event, 'image_text_fallback');
  for (const [i, slide] of state.draft.slides.entries()) {
    assert.equal(slide.visual.type, 'text');
    assert.equal(slide.visual.assetKey, null);
    assert.equal(slide.content, original[i].content);
    assert.deepEqual(slide.evidence, original[i].evidence);
  }
  const request = toImportRequest(state.draft);
  assert.ok(request.slides.every(slide => slide.image === null && slide.layout === 'text'));
});

test('existing valid images are never silently replaced', () => {
  assert.throws(() => fallbackImageToText(fixture(), { key: 'missing', image: { url: 'https://example.com/a.png' } }, 'reason'), /acquired image/);
  assert.throws(() => fallbackImageToText(fixture(), { key: 'unknown', image: null }, 'reason'), /not referenced/);
});

test('warnings remain visible in n8n and are not mixed into article text or the server request', () => {
  const draft = fixture().draft;
  draft.validation = { warnings: [{ code: 'IMAGE_UNAVAILABLE', slides: [1] }] };
  const [transform, http] = ingestionNodes();
  const items = new Function('$input', transform.parameters.jsCode)({ all: () => [{ json: draft }] });
  assert.deepEqual(items[0].json.reviewWarnings, draft.validation.warnings);
  assert.equal(items[0].json.request.reviewWarnings, undefined);
  assert.equal(items[0].json.request.summary, draft.summary);
  assert.equal(http.parameters.jsonBody, '={{ JSON.stringify($json.request) }}');
});
