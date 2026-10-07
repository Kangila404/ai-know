// Revalidate captured executions without editing outputs or making any network/model calls.
// Input shape: {executionId, state, draft, raw?}; keep captures outside Git.
import fs from 'node:fs';
import {createHash} from 'node:crypto';
const w=JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json',import.meta.url),'utf8'));
if(process.argv.length<3)throw new Error('Pass captured execution JSON file paths');
for(const file of process.argv.slice(2)){
  const capture=JSON.parse(fs.readFileSync(file,'utf8')),results=[];
  for(const n of w.nodes.filter(n=>n.parameters?.jsCode?.includes('function validateDraft'))){
    const code=n.parameters.jsCode;
    const validate=new Function(code.slice(0,code.indexOf('function requestModel'))+';return validateDraft;')();
    try{const checked=validate(structuredClone(capture.draft),structuredClone(capture.state));results.push({node:n.name,result:checked?'valid':'skip'});}
    catch(e){results.push({node:n.name,result:'rejected',reason:e.message});}
  }
  console.log(JSON.stringify({executionId:capture.executionId??null,
    modelOutputSha256:createHash('sha256').update(JSON.stringify(capture.draft)).digest('hex'),results,paidApiCalls:0}));
}
