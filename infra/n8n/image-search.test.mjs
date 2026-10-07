import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { imageSearchPlan, imageCandidates, imageLicense, imageHttps, mergeImageCandidates,
  imageShortlist, imageSelectionPayload, imageSelectionSchema, applyImageChoices, rejectImageChoice } from './image-search.mjs';

const flickr=(id='1')=>({id,title:'Computer network',creator:'Photographer',license:'by',license_version:'2.0',
  license_url:'https://creativecommons.org/licenses/by/2.0/',foreign_landing_url:'https://www.flickr.com/photos/owner/'+id,
  url:'https://live.staticflickr.com/1/'+id+'.jpg',width:1200,height:800,filetype:'jpg'});
const candidate=id=>imageCandidates({results:[flickr(id)]},'openverse')[0];

test('specific and conceptual queries search both providers, with bounded result counts',()=>{
  const plan=imageSearchPlan({searchQuery:'PolicyLM | content moderation | ignored'});
  assert.equal(plan.length,4);
  assert.deepEqual(plan.map(x=>x.provider),['commons','openverse','commons','openverse']);
  assert.equal(new URL(plan[0].url).searchParams.get('gsrlimit'),'12');
  assert.equal(new URL(plan[1].url).searchParams.get('license'),'by,cc0,pdm');
});

test('normalize licensed Openverse and Commons images; exclude unsuitable rights, small images and private hosts',()=>{
  const valid=flickr();
  assert.equal(imageCandidates({results:[valid]},'openverse').length,1);
  for(const change of [{license:'by-nc'},{mature:true},{width:100},{creator:''},
    {url:'https://127.0.0.1/a.jpg'},{foreign_landing_url:'https://localhost/admin'},
    {license_url:'https://creativecommons.org/licenses/by-sa/4.0/'},
    {url:'https://live.staticflickr.com.evil.invalid/a.jpg'}])
    assert.equal(imageCandidates({results:[{...valid,...change}]},'openverse').length,0);
  const page={pageid:123,index:1,title:'Communication',imageinfo:[{mime:'image/jpeg',width:800,height:600,
    url:'https://upload.wikimedia.org/wikipedia/commons/test.jpg',descriptionurl:'https://commons.wikimedia.org/wiki/File:Test.jpg',
    extmetadata:{Artist:{value:'<b>Author</b>'},LicenseShortName:{value:'CC0'},LicenseUrl:{value:'https://creativecommons.org/publicdomain/zero/1.0/deed.en/'}}}]};
  assert.equal(imageCandidates({query:{pages:[page]}},'commons')[0].licenseUrl,'https://creativecommons.org/publicdomain/zero/1.0/');
  assert.equal(imageLicense('https://creativecommons.org/licenses/by/2.0/'),'https://creativecommons.org/licenses/by/2.0/');
  assert.equal(imageHttps('https://www.flickr.com:443/photos/x','page'),null);
});

test('merge removes duplicate image URLs and shortlist keeps provider/query diversity within token budget',()=>{
  const a=candidate('1');assert.equal(mergeImageCandidates([a],[{...a,url:a.url+'?tracking=x'}]).length,1);
  const list=Array.from({length:48},(_,i)=>({...candidate(String(i)),provider:i<24?'commons':'openverse',searchQuery:i%2?'specific':'concept'}));
  const short=imageShortlist({candidates:list});assert.equal(short.length,8);
  assert.equal(new Set(short.map(x=>x.provider+':'+x.searchQuery)).size,4);
  const payload=imageSelectionPayload({draft:{title:'제목'.repeat(100),summary:'요약'.repeat(2500)},assets:Array.from({length:5},()=>({key:'a'.repeat(40),description:'목적'.repeat(180),candidates:short.map(c=>({...c,candidateId:'openverse:'+'a'.repeat(36),title:'t'.repeat(140),description:'d'.repeat(320)}))}))});
  assert.ok(JSON.stringify(payload).length<22000);
});

test('only reviewed high-confidence alternatives can replace a failed source page',()=>{
  const state={assets:[{key:'topic',candidates:[candidate('1'),candidate('2')]}],audit:[]};
  const choice={assetKey:'topic',candidateId:'openverse:1',alternativeCandidateIds:['openverse:2'],confidence:'high',reason:'관련 작업 환경'};
  applyImageChoices(state,{selections:[choice]});assert.equal(state.assets[0].selected.candidateId,'openverse:1');
  rejectImageChoice(state.assets[0]);assert.equal(state.assets[0].selected.candidateId,'openverse:2');
  rejectImageChoice(state.assets[0]);assert.equal(state.assets[0].selected,null);
  assert.throws(()=>applyImageChoices(state,{selections:[{...choice,candidateId:'invented'}]}));
  applyImageChoices(state,{selections:[{...choice,confidence:'low'}]});assert.equal(state.assets[0].selected,null);
});

const workflow=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
const runController=state=>new Function('$input',workflow.nodes.find(n=>n.name==='작업 결정').parameters.jsCode)({first:()=>({json:state})})[0].json;
test('controller exhausts search before selecting; suitable existing images bypass generation',()=>{
  const state={phase:'search',config:{allowImageGeneration:true,maxImageGenerations:3,dryRun:false},
    counters:{transitions:0,commonsCalls:0,imageCalls:0},assets:[{key:'topic',searchQuery:'model | computer',candidates:[]}],assetIndex:0,audit:[]};
  let next=runController(state);assert.equal(next.route,2);assert.equal(next.searchRequest.provider,'commons');
  next.assets[0].searchIndex=1;next=runController(next);assert.equal(next.searchRequest.provider,'openverse');
  next.phase='assets';next.assets[0].image={origin:'sourced',url:'https://upload.wikimedia.org/a.jpg'};
  next=runController(next);assert.equal(next.route,4);assert.equal(next.counters.imageCalls,0);
});

test('generation is last resort; disabled generation and exhausted generation budget preserve draft',()=>{
  const state={phase:'assets',config:{allowImageGeneration:true,maxImageGenerations:3,dryRun:false,visualStyle:{illustration:'style'},imageModel:'fixture'},
    counters:{transitions:0,commonsCalls:0,imageCalls:0},assets:[{key:'topic',description:'topic',generationPrompt:'concept',candidates:[]}],assetIndex:0,audit:[],
    draft:{slides:[{sequence:1,visual:{type:'photo',assetKey:'topic'}}]}};
  let next=runController(state);assert.equal(next.route,3);assert.equal(next.counters.imageCalls,1);
  state.config.allowImageGeneration=false;next=runController(state);assert.equal(next.route,4);assert.equal(next.counters.imageCalls,0);
  state.config.allowImageGeneration=true;state.counters.imageCalls=3;next=runController(state);assert.equal(next.route,4);assert.equal(next.draft.slides[0].visual.type,'text');
  state.counters.imageCalls=0;state.assets[0].searchFailures=1;next=runController(state);assert.equal(next.route,4);assert.equal(next.counters.imageCalls,0);
});

test('every embedded Code node compiles and graph references existing nodes',()=>{
  const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
  for(const node of workflow.nodes)if(node.parameters.jsCode)new AsyncFunction(node.parameters.jsCode);
  const names=new Set(workflow.nodes.map(n=>n.name));
  for(const [name,c] of Object.entries(workflow.connections)){
    assert.ok(names.has(name),name);for(const edges of c.main||[])for(const edge of edges)assert.ok(names.has(edge.node),edge.node);
  }
});

test('selection schema permits only searched IDs for each asset and exposes alternative/count bounds',()=>{
  const controller=workflow.nodes.find(n=>n.name==='작업 결정').parameters.jsCode;
  const base=JSON.parse(controller.match(/const IMAGE_SELECTION_SCHEMA = ([^\n]+);/)[1]);
  const s=imageSelectionSchema({assets:[{key:'known',candidates:[candidate('1'),candidate('2')]},{key:'empty',candidates:[]}]},base);
  assert.equal(s.properties.selections.minItems,2);assert.equal(s.properties.selections.maxItems,2);
  const [known,empty]=s.properties.selections.items.anyOf;
  assert.deepEqual(known.properties.candidateId.enum,['openverse:1','openverse:2',null]);
  assert.equal(known.properties.alternativeCandidateIds.maxItems,2);
  assert.deepEqual(empty.properties.candidateId.enum,[null]);assert.equal(empty.properties.alternativeCandidateIds.maxItems,0);
});
