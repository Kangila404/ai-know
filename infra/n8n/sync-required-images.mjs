import fs from 'node:fs';
import * as helpers from './required-images.mjs';
const file=process.argv[2]||new URL('./workflows/ai-know-news-draft.json',import.meta.url);
const wf=JSON.parse(fs.readFileSync(file,'utf8'));
const begin='// BEGIN REQUIRED IMAGES',end='// END REQUIRED IMAGES';
const helper=begin+'\n'+Object.values(helpers).map(f=>f.toString()).join('\n')+'\n'+end;
for(const node of wf.nodes){
 let code=node.parameters?.jsCode;if(!code)continue;
 if(code.includes(begin))code=code.slice(0,code.indexOf(begin))+code.slice(code.indexOf(end)+end.length);
 code=code.replace(/^\n+/,'');
 if(code.includes('function validateDraft'))code=helper+'\n'+code;
 if(code.includes('function validateDraft')) {
  code=code.replace('return {draft:d,assets:[...assets.values()]};','return {draft:d,assets:requiredSlidePlans(d)};');
  code=code.replace(/maxSlides:7/g,'maxSlides:5').replace(/maxAssets:7/g,'maxAssets:5');
 }
 if(node.name==='실행 설정')code=code.replace('allowImageGeneration: false','allowImageGeneration: true').replace(/maxImageGenerations: \d+/,'maxImageGenerations: 5').replace('Math.min(7,Math.max(0,Math.trunc(config.maxImageGenerations)))','Math.min(5,Math.max(0,Math.trunc(config.maxImageGenerations)))').replace('Math.min(3,Math.max(0,Math.trunc(config.maxImageGenerations)))','Math.min(5,Math.max(0,Math.trunc(config.maxImageGenerations)))');
 if(node.name==='작업 결정'){
  code=code.replace(/    \/\/ Reuse an already acquired image[^\n]*\n[\s\S]*?(?=    if \(a.searchFailures)/,'');
  code=code.replace(/    if \(a.searchFailures[^\n]+\n/,'');
  code=code.replace(/if \(!s.config.allowImageGeneration\) \{fallbackImageToText[^\n]+/,
   "if (!s.config.allowImageGeneration) {fail(s,'이미지 생성이 꺼져 있어 완성할 수 없습니다. 빈 카드 저장을 중단합니다.');continue;}");
  code=code.replace(/if \(s.counters.imageCalls>=s.config.maxImageGenerations\) \{fallbackImageToText[^\n]+/,
   "if (s.counters.imageCalls>=s.config.maxImageGenerations) {fail(s,'이미지 생성 한도에 도달했습니다. 저장된 이미지를 복구해 완성하세요.');continue;}");
  code=code.replace('s.counters.imageSearchCalls>28','s.counters.imageSearchCalls>20');
  // A candidate chosen for an earlier slide must move to another licensed candidate or generation.
  const marker="    if (a.selected && !a.pageChecked)";
  if(!code.includes('imageIdentity(b.image)'))code=code.replace(marker,
   "    while(a.selected && s.assets.some(b=>b!==a && b.image && imageIdentity(b.image)===imageIdentity(a.selected))) rejectImageChoice(a);\n"+marker);
 }
 if(node.name==='초안 검증 완료') {
  // Validation mutates old model drafts; keep the already resolved per-slide plan for output.
  code=code.replace('validateDraft(s.draft,s);','validateDraft(clone(s.draft),s);');
  if(!code.includes('assertCompleteImages(slides);'))code=code.replace('const output={','assertCompleteImages(slides);\nconst output={');
 }
 node.parameters.jsCode=code;
}
fs.writeFileSync(file,JSON.stringify(wf,null,2)+'\n');
console.log('Required unique images, bounded generation fallback, and final completeness gate connected');
