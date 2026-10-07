// Run only with n8n stopped, using the deployment tool container and its data volume.
// Reuses an unchanged draft response; optional continuation allows one image-selection
// text request, never draft regeneration or image generation. Does not modify the main workflow.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {DatabaseSync} from 'node:sqlite';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
const {parse}=createRequire(import.meta.url)('/usr/local/lib/node_modules/n8n/node_modules/flatted');
const id=process.argv[2],run=process.argv.includes('--continue-images');
if(!/^\d+$/.test(id||''))throw new Error('Pass the failed execution ID');
const db=new DatabaseSync('/home/node/.n8n/database.sqlite',{readOnly:true});
const row=db.prepare('SELECT e.workflowId,e.status,d.data FROM execution_entity e JOIN execution_data d ON d.executionId=e.id WHERE e.id=?').get(id);
db.close();
if(!row||row.workflowId!=='iwGOpyXhwAq9tON2'||row.status!=='error')throw new Error('Expected a failed main-workflow execution');
const runs=parse(row.data).resultData.runData;
const state=structuredClone(runs['모델 호출 준비']?.[0]?.data.main[0][0].json);
const raw=runs['텍스트 생성 API']?.[0]?.data.main[0][0].json;
if(state?.task!=='draft'||state.config.allowImageGeneration||!state.cacheClaim?.id||!raw)throw new Error('Missing cached draft, or image generation enabled');
const body=raw.body??raw;
const output=body.output?.filter(x=>x.type==='message').flatMap(x=>x.content||[]).find(x=>x.type==='output_text')?.text;
if(body.status!=='completed'||!output)throw new Error('Cannot replay an incomplete response');
const source=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
const helper=source.nodes.find(n=>n.name==='작업 결정').parameters.jsCode;
new Function(helper.slice(0,helper.indexOf('function requestModel'))+';return validateDraft;')()(JSON.parse(output),structuredClone(state));
console.log(JSON.stringify({originalExecutionId:id,generationRecordId:state.cacheClaim.id,
  unchangedModelOutputSha256:createHash('sha256').update(output).digest('hex'),validation:'passed',willContinue:run}));
if(!run)process.exit(0);
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'replay-draft-'));
function cli(args,allowError=false){const r=spawnSync('n8n',args,{encoding:'utf8',timeout:360000,maxBuffer:32*1024*1024});
  if(r.error||(!allowError&&r.status!==0))throw new Error('n8n '+args[0]+' failed (see execution logs)');return r.stdout;}
try{
  const file=path.join(temp,'live.json');cli(['export:workflow','--id='+row.workflowId,'--output='+file]);
  const [live]=JSON.parse(fs.readFileSync(file,'utf8'));
  const wf={id:'aiknow-replay-'+id,name:'AI_KNOW — 실행 '+id+' 캐시 재검증',active:false,
    nodes:live.nodes,connections:live.connections,settings:{...live.settings,executionOrder:'v1'},staticData:null,pinData:{}};
  // Use the current checked-in logic and current live credential references.
  for(const n of wf.nodes){const src=source.nodes.find(x=>x.id===n.id);if(src?.parameters?.jsCode)n.parameters=structuredClone(src.parameters);if(n.type.includes('scheduleTrigger'))n.disabled=true;}
  state.audit.push({event:'unchanged_cached_draft_replay',detail:{originalExecutionId:id,generationRecordId:state.cacheClaim.id}});
  const code=(id,name,jsCode)=>({id,name,type:'n8n-nodes-base.code',typeVersion:2,position:[0,0],parameters:{jsCode}});
  const bootstrap=code('aiknow-replay-input','복구 입력',`return [{json:${JSON.stringify(state)}}];`);
  const existing=structuredClone(wf.nodes.find(n=>n.id==='aiknow-generation-existing'));
  existing.id='aiknow-replay-existing';existing.name='복구 중복 검사';existing.parameters.jsonBody='={{ JSON.stringify({sourceUrls:[$json.current.sourceUrl]}) }}';
  const gate=code('aiknow-replay-gate','복구 상태 확인',`const r=$input.first().json;
if(r.statusCode!==200||!Array.isArray(r.body))throw new Error('Replay duplicate lookup failed');
const s=JSON.parse(JSON.stringify($('복구 입력').first().json));s.alreadyStored=r.body.length>0;
if(s.alreadyStored){s.status='skipped';s.phase='stop';s.reason='이미 저장된 기사입니다.';}
return [{json:s,pairedItem:{item:0}}];`);
  const route={id:'aiknow-replay-route',name:'복구 실행 여부',type:'n8n-nodes-base.switch',typeVersion:3.2,position:[0,0],parameters:{mode:'expression',numberOutputs:2,output:'={{ $json.alreadyStored ? 1 : 0 }}'}};
  const response=code('aiknow-replay-response','보관된 원본 응답',`return [{json:${JSON.stringify(raw)},pairedItem:{item:0}}];`);
  const validation=structuredClone(wf.nodes.find(n=>n.name==='텍스트 응답 검증'));
  validation.id='aiknow-replay-validation';validation.name='보관된 초안 재검증';
  validation.parameters.jsCode=validation.parameters.jsCode.replace("$('모델 호출 준비').itemMatching(0)","$('복구 입력').first()");
  const guard=code('aiknow-replay-call-guard','이미지 선택 호출 제한',`const item=$input.first(),s=item.json;
if(s.task!=='image_selection'||s.counters.textCalls!==2)throw new Error('Replay permits only one image-selection request, never draft regeneration');
return [item];`);
  const image=wf.nodes.find(n=>n.name==='개념 이미지 생성 API');image.type='n8n-nodes-base.code';image.typeVersion=2;
  image.parameters={jsCode:"throw new Error('Image generation is disabled during replay');"};delete image.credentials;
  wf.nodes.push(bootstrap,existing,gate,route,response,validation,guard);
  const edge=node=>({node,type:'main',index:0}),chain=(a,b)=>{wf.connections[a]={main:[[edge(b)]]};};
  chain('수동 실행','복구 입력');chain('복구 입력','복구 중복 검사');chain('복구 중복 검사','복구 상태 확인');chain('복구 상태 확인','복구 실행 여부');
  wf.connections['복구 실행 여부']={main:[[edge('보관된 원본 응답')],[edge('점검 또는 정상 종료')]]};
  chain('보관된 원본 응답','보관된 초안 재검증');chain('보관된 초안 재검증','작업 결정');
  wf.connections['모델 캐시 처리 경로'].main[0]=[edge('이미지 선택 호출 제한')];chain('이미지 선택 호출 제한','텍스트 생성 API');
  const input=path.join(temp,'replay.json');fs.writeFileSync(input,JSON.stringify([wf]));
  cli(['import:workflow','--input='+input,'--activeState=false']);
  cli(['execute','--id='+wf.id,'--rawOutput'],true);
  const check=new DatabaseSync('/home/node/.n8n/database.sqlite',{readOnly:true});
  const latest=check.prepare('SELECT e.id,e.status,d.data FROM execution_entity e JOIN execution_data d ON d.executionId=e.id WHERE e.workflowId=? ORDER BY e.id DESC LIMIT 1').get(wf.id);check.close();
  const result=parse(latest.data).resultData,rd=result.runData;
  const receipt=rd['저장 확인 후 URL 기록']?.[0]?.data.main[0][0].json;
  const draft=rd['초안 검증 완료']?.[0]?.data.main[0][0].json;
  console.log(JSON.stringify({recoveryWorkflow:wf.id,executionId:latest.id,status:latest.status,receipt,
    error:result.error?.message,counters:draft?.run.counters,textUsage:draft?.run.textUsage,
    imageSlides:draft?.slides.filter(s=>s.visual.image).length,slides:draft?.slides.length}));
  if(result.error||(!receipt&&latest.status!=='success'))process.exitCode=1;
}finally{fs.rmSync(temp,{recursive:true,force:true});}
