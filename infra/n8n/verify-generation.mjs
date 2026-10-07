// Full real n8n engine regression. Network I/O is restricted to the isolated
// Spring test server; RSS/article/Commons/OpenAI are recorded-data fixtures.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { DatabaseSync } from 'node:sqlite';
import { createRequire } from 'node:module';
import { configureWorkflow } from './workflow-environment.mjs';
const {parse:parseExecution}=createRequire(import.meta.url)('/usr/local/lib/node_modules/n8n/node_modules/flatted');

const base=process.argv[2], fixture=JSON.parse(fs.readFileSync((process.argv[3]?.startsWith('--') ? null : process.argv[3]) || new URL('./fixtures/generation.json',import.meta.url),'utf8'));
if(base!=='http://host.docker.internal:18081') throw new Error('This check requires the isolated Spring test server on 18081');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'generation-check-'));
function cli(args, allowError=false){
  const r=spawnSync('n8n',args,{encoding:'utf8',timeout:180000,maxBuffer:24*1024*1024});
  if(r.error||(!allowError&&r.status!==0))throw new Error(`n8n ${args[0]} failed: ${r.stderr.slice(-1000)}`);
  return r.stdout;
}
const credential={id:'generation-test-token',name:'Isolated generation test',type:'httpHeaderAuth',
  data:{name:'Authorization',value:'Bearer local-image-smoke-test-token-32-characters'}};
const creds={httpHeaderAuth:{id:credential.id,name:credential.name}};
function code(w,name,jsCode){const n=w.nodes.find(n=>n.name===name);n.type='n8n-nodes-base.code';n.typeVersion=2;n.parameters={jsCode};delete n.credentials;delete n.onError;}
function workflow(label, mode='valid'){
  const source=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
  const w=configureWorkflow(source,{AIKNOW_ENV:'local',AIKNOW_SERVER_BASE_URL:base,
    AIKNOW_MEDIA_PUBLIC_BASE_URL:'http://localhost:18081/media/generated',AIKNOW_DRY_RUN:'false',
    AIKNOW_ALLOW_IMAGE_GENERATION:mode==='generated'?'true':'false',AIKNOW_MAX_IMAGE_GENERATIONS:'1'});
  w.id='generation-regression-'+label;w.name='[ISOLATED TEST] '+label;w.active=false;
  const url='https://techcrunch.com/local-test/'+label;
  const published='2026-10-07T00:00:00.000Z';
  const config=w.nodes.find(n=>n.name==='실행 설정');
  config.parameters.jsCode=config.parameters.jsCode.replace(/dryRun:\s*(true|false)/,'dryRun: false')
    .replace(/allowImageGeneration:\s*(true|false)/,'allowImageGeneration: '+(mode==='generated'));
  code(w,'TechCrunch AI RSS',`return [{json:${JSON.stringify({title:fixture.state.current.sourceTitle,link:url,pubDate:published,content:fixture.state.docs[0].text})}}];`);
  // Pin test time only inside the fixture candidate filter, not the live workflow.
  const candidates=w.nodes.find(n=>n.name==='최근 기사와 중복 검사');
  candidates.parameters.jsCode=candidates.parameters.jsCode.replace('now=Date.now()',"now=Date.parse('2026-10-07T08:00:00Z')");
  const html='<article>'+fixture.state.docs[0].text.replaceAll('&','&amp;').replaceAll('<','&lt;')+'</article>';
  code(w,'원문과 출처 조회',`const s=$input.first().json;
if(s.task==='license'){
  const c=s.assets[s.assetIndex].selected;
  const body=c.candidateId.endsWith(':primary')?'License not found':'<a href="'+c.licenseUrl+'">License</a> '+c.creator;
  return [{json:{statusCode:200,body},pairedItem:{item:0}}];
}
return [{json:${JSON.stringify({statusCode:200,body:html})},pairedItem:{item:0}}];`);
  code(w,'관련 이미지 검색',`const s=$input.first().json;
if(${JSON.stringify(mode)}==='sourced'&&s.searchRequest.provider==='openverse'){
  const results=['primary','alternative'].map(id=>({id,title:'Computer network',creator:'Fixture author',license:'by',license_version:'2.0',
    license_url:'https://creativecommons.org/licenses/by/2.0/',foreign_landing_url:'https://www.flickr.com/photos/fixture/'+id,
    url:'https://live.staticflickr.com/fixture/'+id+'.jpg',width:1200,height:800,filetype:'jpg'}));
  return [{json:{statusCode:200,body:{results}},pairedItem:{item:0}}];
}
return [{json:{statusCode:200,body:s.searchRequest.provider==='openverse'?{results:[]}:{query:{pages:[]}}},pairedItem:{item:0}}];`);
  code(w,'개념 이미지 생성 API',`throw new Error('Paid image calls are forbidden in this regression');`);
  if(mode==='generated')code(w,'개념 이미지 생성 API',`return [{json:{statusCode:200,body:{data:[{b64_json:'iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAYAAACp8Z5+AAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAQSURBVBhXY2Bg+P+fgTIAAIDDAf9xL9E8AAAAAElFTkSuQmCC'}]}},pairedItem:{item:0}}];`);
  code(w,'텍스트 생성 API',`const state=$input.first().json;const draft=${JSON.stringify(fixture.draft)};
if(state.task==='image_selection'){
  const selections=state.assets.map(a=>({assetKey:a.key,candidateId:'openverse:primary',alternativeCandidateIds:['openverse:alternative'],confidence:'high',reason:'Fixture related concept'}));
  return [{json:{statusCode:200,body:{id:'fixture-selection',status:'completed',model:'fixture-no-paid-api',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify({selections})}]}]}},pairedItem:{item:0}}];
}
const catalog=state.evidenceCatalog;
const refs=quotes=>[...new Set(quotes.flatMap(q=>{
  const doc=state.docs.find(d=>d.id===q.sourceId),start=doc.text.indexOf(q.quote);
  if(start<0)throw new Error('Fixture quote is not literal source evidence');
  const end=start+q.quote.length;
  return catalog.filter(c=>c.sourceId===q.sourceId).filter(c=>{
    const at=doc.text.indexOf(c.quote);return at>=0&&at<end&&at+c.quote.length>start;
  }).map(c=>c.id);
}))];
draft.evidence=refs(draft.evidence);for(const slide of draft.slides)slide.evidence=refs(slide.evidence);
if(${JSON.stringify(mode)}==='invalid')draft.evidence=['source-1:nonexistent'];
if(${JSON.stringify(mode)}==='japanese')draft.title='Lambdaが資金調達へ';
if(${JSON.stringify(mode)}==='mixed')draft.slides[0].content='기사에 따르면、資金を調達しました。';
if(${JSON.stringify(mode)}==='sourced'){
  draft.slides=draft.slides.slice(0,5);
  draft.slides.forEach((slide,i)=>{slide.visual=i<4?{type:'photo',description:'관련 컴퓨터 개념 이미지',dataJson:null,assetKey:'topic_'+(i%2),
    searchQuery:'computer network | online communication',generationPrompt:'Concept illustration'}:
    {type:'text',description:'마무리 설명',dataJson:null,assetKey:null,searchQuery:null,generationPrompt:null};});
}
if(${JSON.stringify(mode)}==='generated'){
  draft.slides.forEach(slide=>{slide.visual={type:'illustration',description:'기사 주제의 개념 설명',dataJson:null,assetKey:'topic',
    searchQuery:'computer network | online communication',generationPrompt:'Concept illustration'};});
}
return [{json:{statusCode:200,body:{id:'fixture-response',status:'completed',model:'fixture-no-paid-api',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(draft)}]}],usage:{input_tokens:0,output_tokens:0,total_tokens:0}}},pairedItem:{item:0}}];`);
  for(const n of w.nodes.filter(n=>n.type==='n8n-nodes-base.httpRequest')){
    if(!n.id.startsWith('aiknow-'))throw new Error('Unexpected outbound HTTP node '+n.name);
    const u=new URL(n.parameters.url);n.parameters.url=base+u.pathname;n.credentials=creds;
  }
  return w;
}
function execute(w){
  const f=path.join(temp,'workflow.json');fs.writeFileSync(f,JSON.stringify([w]));
  cli(['import:workflow','--input='+f,'--activeState=false']);
  const raw=cli(['execute','--id='+w.id,'--rawOutput'],true);
  const parsed=(raw.match(/^\{[\s\S]*?^\}/gm)??[]).map(s=>{try{return JSON.parse(s);}catch{return null;}}).find(x=>x?.data?.resultData);
  if(parsed)return parsed.data.resultData;
  const db=new DatabaseSync('/home/node/.n8n/database.sqlite',{readOnly:true});
  try{
    const row=db.prepare('SELECT d.data FROM execution_data d JOIN execution_entity e ON e.id=d.executionId WHERE e.workflowId=? ORDER BY e.id DESC LIMIT 1').get(w.id);
    if(row)return parseExecution(row.data).resultData;
  }finally{db.close();}
  throw new Error('Missing n8n execution result: '+raw.slice(-3500));
}
function verifyImage(nonce) {
  const generated=workflow('generated-'+nonce,'generated'),imageInterrupted=structuredClone(generated);
  code(imageInterrupted,'서버 검수 대기 저장',"throw new Error('Simulated outage after image storage');");
  const imageStopped=execute(imageInterrupted);assert.ok(imageStopped.error,imageStopped.error?.message);
  if(!imageStopped.runData['이미지 캐시 저장 확인']){
    const draft=imageStopped.runData['초안 검증 완료']?.[0]?.data?.main?.[0]?.[0]?.json;
    console.log(JSON.stringify({nodes:Object.keys(imageStopped.runData),warnings:draft?.validation?.warnings,counters:draft?.run?.counters,
      config:imageStopped.runData['실행 설정']?.[0]?.data?.main?.[0]?.[0]?.json?.config}));
  }
  assert.ok(imageStopped.runData['이미지 캐시 저장 확인'],imageStopped.error?.message);
  const imageRecovered=execute(generated);assert.ok(!imageRecovered.error,imageRecovered.error?.message);
  assert.equal(imageRecovered.runData['개념 이미지 생성 API'],undefined);
  assert.equal(imageRecovered.runData['텍스트 생성 API'],undefined);
  assert.ok(imageRecovered.runData['저장 이미지 재사용']);
  const generatedRequest=imageRecovered.runData['서버 요청 변환'][0].data.main[0][0].json.request;
  assert.ok(generatedRequest.slides.every(s=>s.image?.url?.startsWith('http://localhost:18081/media/generated/')));
  assert.equal(imageRecovered.runData['저장 확인 후 URL 기록'][0].data.main[0][0].json.httpStatus,202);
  console.log('PASS generated image: immediate multipart upload/cache -> interrupted import -> replay stored image -> PENDING without another generation');
}
try{
  const f=path.join(temp,'credential.json');fs.writeFileSync(f,JSON.stringify([credential]),{mode:0o600});cli(['import:credentials','--input='+f]);
  const nonce=Date.now().toString();
  if(process.argv.includes('--image-only')) { verifyImage(nonce); }
  else {
  const valid=workflow('valid-'+nonce),first=execute(valid);
  assert.ok(!first.error,first.error?.message);
  const receipt=first.runData['저장 확인 후 URL 기록'][0].data.main[0][0].json;
  assert.equal(receipt.status,'PENDING');assert.equal(receipt.httpStatus,202);
  console.log('PASS full graph: article -> evidence IDs -> response storage -> validation -> image fallback -> PENDING');
  const repeat=execute(valid);assert.ok(!repeat.error,repeat.error?.message);
  assert.equal(repeat.runData['텍스트 생성 API'],undefined);
  assert.equal(repeat.runData['점검 또는 정상 종료'][0].data.main[0][0].json.status,'skipped');
  console.log('PASS saved article repeat: no model node or document fetch');
  assert.equal(repeat.runData['원문과 출처 조회'],undefined);
  const invalid=workflow('invalid-'+nonce,'invalid'),bad=execute(invalid);
  assert.ok(bad.error);assert.match(bad.error.message,/없는 근거 ID/);
  assert.ok(bad.runData['모델 원본 응답 보관']);assert.equal(bad.runData['서버 검수 대기 저장'],undefined);
  const retry=execute(invalid);assert.ok(retry.error);assert.equal(retry.runData['텍스트 생성 API'],undefined);
  assert.ok(retry.runData['저장된 모델 응답 재사용']);
  console.log('PASS invalid evidence: no DB submission; repeat reuses stored response without a model call');
  for(const mode of ['japanese','mixed']){
    const wrong=execute(workflow(mode+'-'+nonce,mode));assert.ok(wrong.error);assert.match(wrong.error.message,/한국어 형식 오류/);
    assert.ok(wrong.runData['모델 원본 응답 보관']);assert.equal(wrong.runData['서버 검수 대기 저장'],undefined);
  }
  console.log('PASS Japanese title and mixed-language body are rejected with field-specific errors, original responses retained');
  const recovery=workflow('recovery-'+nonce),interrupted=structuredClone(recovery);
  code(interrupted,'서버 검수 대기 저장',"throw new Error('Simulated import outage after generation');");
  const stopped=execute(interrupted);assert.ok(stopped.error);assert.ok(stopped.runData['모델 원본 응답 보관']);
  const recovered=execute(recovery);assert.ok(!recovered.error,recovered.error?.message);
  assert.equal(recovered.runData['텍스트 생성 API'],undefined);
  assert.equal(recovered.runData['저장 확인 후 URL 기록'][0].data.main[0][0].json.httpStatus,202);
  console.log('PASS downstream failure recovery: cached response -> PENDING, no repeated model call');
  const sourced=execute(workflow('sourced-'+nonce,'sourced'));assert.ok(!sourced.error,sourced.error?.message);
  const request=sourced.runData['서버 요청 변환'][0].data.main[0][0].json.request;
  assert.equal(request.slides.filter(s=>s.image).length,4);assert.ok(request.titleImage);
  assert.ok(request.slides[0].image.url.endsWith('alternative.jpg'));
  assert.match(request.slides[0].image.credit,/CC BY 2.0/);
  assert.equal(sourced.runData['개념 이미지 생성 API'],undefined);
  assert.equal(sourced.runData['저장 확인 후 URL 기록'][0].data.main[0][0].json.httpStatus,202);
  const output=sourced.runData['초안 검증 완료'][0].data.main[0][0].json;
  assert.ok(output.run.audit.some(a=>a.event==='license_rejected'));
  assert.equal(output.run.counters.openverseCalls,4);assert.equal(output.run.counters.commonsCalls,4);
  console.log('PASS image-first graph: two providers -> relevance selection -> rejected primary -> verified alternative -> 4/5 images + cover -> PENDING');
  verifyImage(nonce);
  console.log(JSON.stringify({passed:10,paidApiCalls:0,productionDatabaseWrites:0,firstReceipt:receipt}));
  }
}finally{fs.rmSync(temp,{recursive:true,force:true});}
