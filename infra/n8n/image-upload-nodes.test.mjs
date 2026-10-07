import test from 'node:test';
import assert from 'node:assert/strict';
import { prepareImageUploads, applyUploadedImages, imageUploadNodes } from './image-upload-nodes.mjs';
import { toImportRequest } from './ingestion-nodes.mjs';

const image = () => ({ origin: 'generated', url: null, binaryProperty: 'image_cover' });
const item = () => ({ json: { sourceTitle: 'source', sourceUrl: 'https://example.test/news',
  publishedAt: '2026-10-07T00:00:00Z', title: '제목', summary: '요약',
  slides: [1, 2].map(sequence => ({ sequence, title: '제목', content: '본문', visual: { type: 'illustration', image: image() } })) },
  binary: { image_cover: { id: 'filesystem-v2:existing-image', mimeType: 'image/png' } } });
const receipt = key => ({ json: { statusCode: 201, body: { key, url: 'https://media.example.test/' + key, contentType: 'image/png', size: 500 } } });

test('local HTTP uploads round-trip only under the configured base, production and sourced HTTP stay blocked', () => {
  const media = { mode: 'local', publicBaseUrl: 'http://localhost:8080/media/generated' };
  const prepared = prepareImageUploads([item()]);
  const response = receipt('a'.repeat(64) + '.png');
  response.json.body.url = media.publicBaseUrl + '/' + response.json.body.key;
  assert.throws(() => applyUploadedImages(prepared, [response]), /invalid/);
  assert.throws(() => applyUploadedImages(prepared, [response], { ...media, mode: 'mounted' }), /invalid/);
  const draft = applyUploadedImages(prepared, [response], media)[0].json;
  assert.equal(toImportRequest(draft, media).slides[0].image.url, response.json.body.url);
  assert.throws(() => toImportRequest(draft), /HTTPS/);
  assert.throws(() => applyUploadedImages(prepared, [response], { ...media, publicBaseUrl: media.publicBaseUrl + '/different' }), /invalid/);
  draft.slides[0].visual.image.origin = 'sourced';
  draft.slides[0].visual.image.sourceUrl = 'https://example.test/source';
  assert.throws(() => toImportRequest(draft, media), /HTTPS/);
});

test('uploads each shared binary once, preserves binary references and writes all slide URLs', () => {
  const original = item(), prepared = prepareImageUploads([original]);
  assert.equal(prepared.length, 1);
  assert.equal(prepared[0].binary.file.id, original.binary.image_cover.id);
  const result = applyUploadedImages(prepared, [receipt('a'.repeat(64) + '.png')])[0].json;
  const request = toImportRequest(result);
  assert.equal(request.slides[0].image.url, request.slides[1].image.url);
  assert.equal(request.slides[0].image.origin, 'GENERATED');
  assert.equal(request.slides[0].image.credit, 'AI 생성 이미지');
  assert.equal(original.json.slides[0].visual.image.url, null);
});

test('no-image drafts bypass upload and sourced images retain attribution', () => {
  const original = item();
  original.json.slides[0].visual.image = null;
  original.json.slides[1].visual.image = { origin: 'sourced', url: 'https://example.test/a.png', credit: 'Author' };
  const prepared = prepareImageUploads([original]);
  assert.equal(prepared[0].json.uploadRequired, false);
  assert.deepEqual(applyUploadedImages(prepared, prepared)[0].json, original.json);
});

test('partial uploads, failed responses and missing binaries prevent DB import', () => {
  const original = item(), prepared = prepareImageUploads([original]);
  assert.throws(() => applyUploadedImages(prepared, []), /missing/);
  const bad = receipt('a'.repeat(64) + '.png'); bad.json.statusCode = 503;
  assert.throws(() => applyUploadedImages(prepared, [bad]), /invalid/);
  delete original.binary;
  assert.throws(() => prepareImageUploads([original]), /binary is missing/);
});

test('pairs different uploaded images correctly, does not forward credentials to redirects', () => {
  const original = item(); original.json.slides[1].visual.image.binaryProperty = 'second';
  original.binary.second = { id: 'filesystem-v2:second' };
  const prepared = prepareImageUploads([original]);
  const a = receipt('a'.repeat(64) + '.png'), b = receipt('b'.repeat(64) + '.png');
  a.pairedItem = { item: 0 }; b.pairedItem = { item: 1 };
  const result = applyUploadedImages(prepared, [b, a])[0].json;
  assert.equal(result.slides[0].visual.image.url, a.json.body.url);
  assert.equal(result.slides[1].visual.image.url, b.json.body.url);
  for (const n of imageUploadNodes('http://server:8080', {}).filter(n => n.type.endsWith('.httpRequest'))) {
    assert.equal(n.parameters.options.redirect.redirect.followRedirects, false);
    assert.equal(n.retryOnFail, false);
  }
});
