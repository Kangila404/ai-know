// Imports source files as PENDING submissions. Never approves or publishes.
// Read the token from stdin, never from a command-line argument.
import fs from 'node:fs';
import {toImportRequest} from './ingestion-nodes.mjs';
const pack=JSON.parse(fs.readFileSync(new URL('./content/ai-theory-starter.json',import.meta.url),'utf8'));
if(pack.intendedContentType!=='AI_THEORY'||pack.reviewStatus!=='PENDING'||pack.cards.length!==10)
  throw new Error('Expected ten theory drafts awaiting review');
const requests=pack.cards.map(card=>toImportRequest({
  sourceTitle:'AI_KNOW 이론 초안 / 참고: '+card.sourceTitle,sourceUrl:card.sourceUrl,publishedAt:pack.preparedAt,
  title:card.title,summary:card.summary,keyPoints:card.slides.slice(0,3).map(slide=>slide[0]),titleImage:null,
  slides:card.slides.map(([title,content],i)=>({sequence:i+1,title,content,layout:'text',image:null}))
}));
if(new Set(requests.map(r=>r.sourceUrl)).size!==10||requests.some(r=>r.slides.length!==5))
  throw new Error('Expected unique sources and five slides per theory draft');
if(process.argv.includes('--check')){
  console.log(JSON.stringify({drafts:requests.length,slides:requests.reduce((sum,r)=>sum+r.slides.length,0),status:'PENDING',intendedContentType:'AI_THEORY'}));
}else{
  const base=new URL(process.argv[2]);
  if(base.username||base.password||base.search||base.hash||base.pathname!=='/'
    ||!(base.protocol==='https:'||(base.protocol==='http:'&&['server','127.0.0.1','localhost','host.docker.internal'].includes(base.hostname))))
    throw new Error('Pass the trusted backend origin, without a path or credentials');
  const token=fs.readFileSync(0,'utf8').trim();
  if(token.length<32||/\s/.test(token))throw new Error('Missing ingestion credential on stdin');
  for(const request of requests){
    const response=await fetch(new URL('/internal/v1/card-news/import',base),{method:'POST',redirect:'error',
      signal:AbortSignal.timeout(30000),headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(request)});
    if(response.status!==202)throw new Error('Theory import failed: HTTP '+response.status+' ('+request.title+')');
    const receipt=await response.json();
    if(!Number.isInteger(receipt.id)||!['PENDING','APPROVED','DENIED'].includes(receipt.status))throw new Error('Invalid import receipt');
    console.log(JSON.stringify({title:request.title,submissionId:receipt.id,status:receipt.status,cardNewsId:receipt.cardNewsId}));
  }
}
