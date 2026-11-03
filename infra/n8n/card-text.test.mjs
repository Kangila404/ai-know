import test from 'node:test';
import assert from 'node:assert/strict';
import {formatCardText} from './card-text.mjs';
import {ingestionNodes} from './ingestion-nodes.mjs';

test('sentence breaks preserve paragraphs, numbers, versions and URLs without changing words',()=>{
 const source='가격은 1.25달러입니다. API v2.1을 씁니다.\n\n문서 https://example.com/a.b 를 참고합니다. 다음 문장입니다.';
 const expected='가격은 1.25달러입니다.\nAPI v2.1을 씁니다.\n\n문서 https://example.com/a.b 를 참고합니다.\n다음 문장입니다.';
 assert.equal(formatCardText(source),expected);
 assert.equal(formatCardText(expected),expected);
 assert.equal(formatCardText('1. 핵심 항목입니다. 다음입니다.'),'1. 핵심 항목입니다.\n다음입니다.');
 assert.equal(formatCardText(source).replace(/\s/g,''),source.replace(/\s/g,''));
});
test('deployed import node includes its formatter and stores literal newlines',()=>{
 const run=new Function('$input',ingestionNodes()[0].parameters.jsCode);
 const draft={sourceTitle:'Source',sourceUrl:'https://example.com/news',publishedAt:'2026-10-26T00:00:00Z',title:'제목',summary:'요약입니다. 다음입니다.',slides:[{sequence:1,title:'제목',content:'첫 문장입니다. 두 번째입니다.'}]};
 const result=run({all:()=>[{json:draft}]})[0].json.request;
 assert.equal(result.slides[0].content,'첫 문장입니다.\n두 번째입니다.');
 assert.equal(result.summary,'요약입니다.\n다음입니다.');
});
