// Read-only application check: feed one known saved source URL to the current
// workflow. Every external/paid/write HTTP node is replaced with a hard guard.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
const sourceUrl=process.argv[2];
if(!sourceUrl?.startsWith('https://techcrunch.com/'))throw new Error('Pass a saved TechCrunch source URL');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'existing-news-check-'));
function cli(args){const r=spawnSync('n8n',args,{encoding:'utf8',timeout:180000,maxBuffer:16*1024*1024});
  if(r.error||r.status!==0)throw new Error('n8n check failed: '+r.stderr.slice(-1000));return r.stdout;}
try{
  const exportFile=path.join(temp,'main.json');cli(['export:workflow','--id=iwGOpyXhwAq9tON2','--output='+exportFile]);
  const [w]=JSON.parse(fs.readFileSync(exportFile,'utf8'));w.id='aiknow-existing-news-check';w.name='AI_KNOW [CHECK] 기존 기사 재생성 차단';w.active=false;w.staticData=null;w.pinData={};
  const config=w.nodes.find(n=>n.name==='실행 설정');config.parameters.jsCode=config.parameters.jsCode.replace(/allowImageGeneration:\s*(true|false)/,'allowImageGeneration: false');
  for(const n of w.nodes){
    if(n.name==='TechCrunch AI RSS'){
      n.type='n8n-nodes-base.code';n.typeVersion=2;n.parameters={jsCode:`return [{json:${JSON.stringify({title:'Saved source duplicate check',link:sourceUrl,pubDate:new Date().toISOString(),content:'Duplicate lookup only'})}}];`};
    }else if(n.type==='n8n-nodes-base.httpRequest'&&n.id!=='aiknow-generation-existing'){
      n.type='n8n-nodes-base.code';n.typeVersion=2;n.parameters={jsCode:"throw new Error('Unexpected network/write/model call in duplicate check');"};delete n.credentials;
    }
  }
  const file=path.join(temp,'check.json');fs.writeFileSync(file,JSON.stringify([w]));cli(['import:workflow','--input='+file,'--activeState=false']);
  const output=cli(['execute','--id='+w.id,'--rawOutput']);
  const result=(output.match(/^\{[\s\S]*?^\}/gm)??[]).map(x=>{try{return JSON.parse(x);}catch{return null;}}).find(x=>x?.data?.resultData)?.data.resultData;
  assert.ok(result);assert.ok(!result.error,result.error?.message);
  const final=result.runData['점검 또는 정상 종료'][0].data.main[0][0].json;
  assert.equal(final.status,'skipped');assert.equal(final.counters.textCalls,0);assert.equal(final.counters.documentCalls,0);
  assert.equal(result.runData['텍스트 생성 API'],undefined);
  const existing=result.runData['DB 기존 기사 조회'][0].data.main[0][0].json.body;
  assert.equal(existing.length,1);assert.equal(existing[0].sourceUrl,sourceUrl);
  console.log(JSON.stringify({status:final.status,existing,paidApiCalls:0,databaseWrites:0}));
}finally{fs.rmSync(temp,{recursive:true,force:true});}
