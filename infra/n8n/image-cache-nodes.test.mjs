import test from 'node:test';
import assert from 'node:assert/strict';
import { imageCacheNodes, imageCacheConnections } from './image-cache-nodes.mjs';

const media={mode:'local',publicBaseUrl:'http://localhost:8080/media/generated'};
const nodes=imageCacheNodes('http://server:8080',{},media);
test('stored image receipt restores an asset without OpenAI or binary data', () => {
  const key='a'.repeat(64)+'.png',receipt={key,url:media.publicBaseUrl+'/'+key,contentType:'image/png',size:300};
  const state={assets:[{key:'concept',generationPrompt:'Concept'}],assetIndex:0,counters:{},imageClaim:{decision:'REPLAY',responseJson:JSON.stringify(receipt)}};
  const run=new Function('$input',nodes.find(n=>n.id==='aiknow-image-replay').parameters.jsCode);
  const result=run({first:()=>({json:state})})[0];
  assert.equal(result.json.assets[0].image.storageKey,key);
  assert.equal(result.json.assets[0].image.url,receipt.url);
  assert.equal(result.json.assetIndex,1);assert.equal(result.json.counters.imageCacheHits,1);assert.equal(result.binary,undefined);
  receipt.url='http://untrusted.example/'+key;state.imageClaim.responseJson=JSON.stringify(receipt);
  assert.throws(()=>run({first:()=>({json:state})}),/invalid/);
});
test('uncertain image reservation blocks the paid branch', () => {
  const state={assets:[{key:'selection'}],assetIndex:0,counters:{},audit:[]};
  const run=new Function('$','$input',nodes.find(n=>n.id==='aiknow-image-prepare').parameters.jsCode);
  const result=run(()=>({itemMatching:()=>({json:state})}),{first:()=>({json:{statusCode:200,body:{decision:'BLOCKED',id:4,reason:'uncertain'}}})})[0].json;
  assert.equal(result.imageCacheRoute,2);assert.equal(result.counters.paidImageCalls,0);assert.equal(result.status,'blocked');
  assert.match(nodes.find(n=>n.id==='aiknow-image-claim').parameters.jsonBody,/image_generation_/);
  assert.equal(imageCacheConnections()['이미지 캐시 처리 경로'].main[2][0].node,'점검 또는 정상 종료');
});
