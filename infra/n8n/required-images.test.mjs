import test from 'node:test';
import assert from 'node:assert/strict';
import {requiredSlidePlans,assertCompleteImages} from './required-images.mjs';
test('cached text and shared image plans get distinct contextual prompts without altering editorial text',()=>{
 const draft={slides:[1,2,3].map(sequence=>({sequence,title:'Title '+sequence,content:'Body '+sequence,visual:{type:sequence===3?'text':'photo',assetKey:'shared',description:'Concept',generationPrompt:'Draw a concept'}}))};
 const before=structuredClone(draft);const assets=requiredSlidePlans(draft);
 assert.equal(new Set(assets.map(a=>a.key)).size,3);
 assert.equal(new Set(assets.map(a=>a.generationPrompt)).size,3);
 draft.slides.forEach((s,i)=>{assert.equal(s.content,before.slides[i].content);assert.equal(s.visual.type,'illustration');});
});
test('no missing image, empty body, or source reused under a different CDN URL can pass final gate',()=>{
 const slides=[1,2].map(sequence=>({sequence,title:'Title',content:'Body',image:{url:'https://media.example/'+sequence+'.png'}}));
 assertCompleteImages(slides);
 assert.throws(()=>assertCompleteImages([{...slides[0],image:null}]),/누락/);
 assert.throws(()=>assertCompleteImages([{...slides[0],content:' '}]),/빈/);
 assert.throws(()=>assertCompleteImages([slides[0],{...slides[1],image:{url:slides[0].image.url+'?preview=320'}}]),/중복/);
 const samePage=slides.map(s=>({...s,image:{...s.image,sourceUrl:'https://commons.wikimedia.org/wiki/File:Same.jpg'}}));
 assert.throws(()=>assertCompleteImages(samePage),/중복/);
});
