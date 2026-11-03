import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { toImportRequest, ingestionNodes } from './ingestion-nodes.mjs';

const draft = () => ({ sourceTitle: 'Source', sourceUrl: 'https://example.com/news',
  publishedAt: 'Wed, 07 Oct 2026 00:00:00 +0000', title: '제목', summary: '요약',
  slides: [{ sequence: 1, title: '슬라이드', content: '내용', visual: { type: 'photo', image: {
    url: 'https://example.com/image.png', origin: 'sourced', sourcePageUrl: 'https://example.com/image',
    creator: 'Author', license: 'CC BY 4.0', licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  } } }] });

test('maps n8n draft into the server contract and preserves attribution', () => {
  const converted = toImportRequest(draft());
  assert.equal(converted.publishedAt, '2026-10-07T00:00:00.000Z');
  assert.equal(converted.slides[0].layout, 'photo');
  assert.equal(converted.slides[0].image.origin, 'SOURCE');
  assert.match(converted.slides[0].image.credit, /Author.*CC BY 4.0/);
  assert.equal(converted.slides[0].image.sourceUrl, 'https://example.com/image');
});

test('Commons tracking-only URLs allow client thumbnails while signed URLs and credits stay intact', () => {
  const input=draft(),im=input.slides[0].visual.image;
  const original='https://upload.wikimedia.org/wikipedia/commons/a/ab/Example%20Image.jpg';
  im.url=original+'?utm_source=commons&utm_campaign=imageinfo';
  const result=toImportRequest(input).slides[0].image;
  assert.equal(result.url,original);assert.match(result.credit,/Author.*CC BY 4.0/);
  im.url=original+'?signature=keep&utm_source=commons';
  assert.equal(toImportRequest(input).slides[0].image.url,im.url);
});

test('embedded import runs without URL globals, as in the n8n Code task runner', () => {
  const input=draft();
  input.slides[0].content='첫 문장입니다. 값은 1.25입니다.';
  input.slides[0].visual.image.url='https://upload.wikimedia.org/wikipedia/commons/a/ab/Image.jpg?utm_source=commons';
  const context=vm.createContext({$input:{all:()=>[{json:input}]}});
  assert.equal(vm.runInContext('typeof URL',context),'undefined');
  const output=vm.runInContext('(function(){'+ingestionNodes()[0].parameters.jsCode+'})()',context);
  assert.equal(output[0].json.request.slides[0].image.url,'https://upload.wikimedia.org/wikipedia/commons/a/ab/Image.jpg');
  assert.equal(output[0].json.request.slides[0].content,'첫 문장입니다.\n값은 1.25입니다.');
});

test('does not silently lose generated images without permanent storage', () => {
  const input = draft();
  input.slides[0].visual.image = { origin: 'generated', url: null, binaryProperty: 'image_1' };
  assert.throws(() => toImportRequest(input), /permanent HTTPS URL/);
  input.slides[0].visual.image.url = 'https://example.com/generated.png';
  assert.equal(toImportRequest(input).slides[0].image.origin, 'GENERATED');
});

test('rejects malformed ordering and missing source attribution', () => {
  const input = draft();
  input.slides[0].sequence = 2;
  assert.throws(() => toImportRequest(input), /sequential/);
  input.slides[0].sequence = 1;
  delete input.slides[0].visual.image.sourcePageUrl;
  assert.throws(() => toImportRequest(input), /image source/);
});

test('HTTP errors stop execution before the history node; redirects do not forward credentials', () => {
  const request = ingestionNodes()[1];
  assert.equal(request.onError, undefined);
  assert.equal(request.parameters.options.response.response.neverError, undefined);
  assert.equal(request.parameters.options.redirect.redirect.followRedirects, false);
});
