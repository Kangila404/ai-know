import fs from 'node:fs';
import { numericValues, assertSupportedNumbers, parseVisualJson, normalizeImagePlan } from './draft-validation.mjs';
import { evidenceCatalog, resolveEvidence, evidenceRequest } from './evidence-catalog.mjs';
import { koreanPattern, koreanDraftSchema, assertKoreanText, validateKoreanDraft } from './korean-draft.mjs';
import { draftLimits, applyDraftLimits, assertEvidence } from './draft-contract.mjs';

const file = process.argv[2] ?? new URL('./workflows/ai-know-news-draft.json', import.meta.url);
const wf = JSON.parse(fs.readFileSync(file, 'utf8'));
const begin = '// BEGIN NORMALIZED EVIDENCE HELPERS';
const end = '// END NORMALIZED EVIDENCE HELPERS';
const helpers = `${begin}\n${draftLimits.toString()}\n${applyDraftLimits.toString()}\n${assertEvidence.toString()}\n${numericValues.toString()}\n${assertSupportedNumbers.toString()}\n${parseVisualJson.toString()}\n${normalizeImagePlan.toString()}\n${koreanPattern.toString()}\n${koreanDraftSchema.toString()}\n${assertKoreanText.toString()}\n${validateKoreanDraft.toString()}\n${evidenceCatalog.toString()}\n${resolveEvidence.toString()}\n${evidenceRequest.toString()}\n${end}\n`;
let updated = 0;
for (const node of wf.nodes) {
  let code = node.parameters?.jsCode;
  if (!code?.includes('function validateDraft')) continue;
  if (code.includes(begin)) {
    const start = code.indexOf(begin), stop = code.indexOf(end, start);
    if (stop < 0) throw new Error('Unterminated helper block');
    code = code.slice(0, start) + helpers + code.slice(stop + end.length + 1);
  } else {
    code = code.replace('function validateDraft', helpers + 'function validateDraft');
  }
  code = code.replace('data=JSON.parse(v.dataJson)', 'data=parseVisualJson(v.dataJson)');
  // Idempotent regeneration, including old helper orderings.
  code = code.replace(/function validateDraft\(d,\s*s\) \{\n(?:  (?:resolveEvidence\(d, s\.evidenceCatalog\)|validateKoreanDraft\(d\));\n)*/,
    'function validateDraft(d, s) {\n  validateKoreanDraft(d);\n  resolveEvidence(d, s.evidenceCatalog);\n');
  code=code.replace(/function validEvidence\([\s\S]*?\n}\n(?=function parseVisualData)/,'');
  code=code.replace("if (!validEvidence(d.evidence,s.docs)) throw new Error('제목/요약의 인용문이 원문과 다릅니다. 저장된 응답을 검토하세요.');",
    "assertEvidence(d.evidence,s.docs,null,'제목/요약');");
  code=code.replace("if (!Array.isArray(d.slides)||d.slides.length<3||d.slides.length>7)",
    "if (!Array.isArray(d.slides)||d.slides.length<draftLimits().minSlides||d.slides.length>draftLimits().maxSlides)");
  code=code.replace('x.title.length>120||x.content.length>1200','x.title.length>draftLimits().slideTitle||x.content.length>draftLimits().slideContent');
  code=code.replace("      ||!validEvidence(x.evidence,s.docs,x.sourceIds)) throw new Error('슬라이드 출처/인용 오류');",
    "      ) throw new Error('슬라이드 '+x.sequence+': 출처 ID 오류');\n    assertEvidence(x.evidence,s.docs,x.sourceIds,'슬라이드 '+x.sequence);");
  code=code.replace('if (assets.size>5)', 'if (assets.size>draftLimits().maxAssets)');
  if(!code.includes("제목/요약 길이 상한 초과"))code=code.replace("  const assets = new Map();",`  if(d.title.length>draftLimits().title||d.summary.length>draftLimits().summary)throw new Error('제목/요약 길이 상한 초과');
  if(Object.values(d.plan).some(v=>typeof v!=='string'||v.length>draftLimits().plan))throw new Error('구성 계획 길이 상한 초과');
  const assets = new Map();`);
  if(!code.includes("시각 설명 길이 상한 초과"))code=code.replace("    x.visual.data=parseVisualData(x);", "    if(x.visual.description.length>draftLimits().description)throw new Error('슬라이드 '+x.sequence+': 시각 설명 길이 상한 초과');\n    x.visual.data=parseVisualData(x);");
  code = code.replace("if (!/[가-힣]/.test(d.title) || !/[가-힣]/.test(d.summary) || !validEvidence(d.evidence,s.docs)) throw new Error('제목/요약 근거 또는 한국어 누락');",
    "if (!/[가-힣]/.test(d.title) || !/[가-힣]/.test(d.summary)) throw new Error('제목 또는 요약의 한국어 누락');\n  if (!validEvidence(d.evidence,s.docs)) throw new Error('제목/요약의 인용문이 원문과 다릅니다. 저장된 응답을 검토하세요.');");
  code = code.replace("requestModel(s,'draft',DRAFT_PROMPT,{article:s.current.sourceTitle,sources:s.docs,visualStyle:s.config.visualStyle},DRAFT_SCHEMA);break;",
    "const indexed=evidenceRequest(s,DRAFT_SCHEMA);\n    requestModel(s,'draft',DRAFT_PROMPT,{article:s.current.sourceTitle,sources:indexed.sources,visualStyle:s.config.visualStyle},indexed.schema);break;");
  if (code.includes('const DRAFT_PROMPT =')) {
    const startPrompt = code.indexOf('const DRAFT_PROMPT = ') + 'const DRAFT_PROMPT = '.length;
    const endPrompt = code.indexOf(';\nconst IMAGE_SELECTION_PROMPT', startPrompt);
    let prompt = JSON.parse(code.slice(startPrompt, endPrompt));
    if(!prompt.includes('출력 언어는 ko-KR'))prompt='출력 언어는 ko-KR 한국어다. 제목, 요약, 계획, 슬라이드 제목·본문·시각 설명은 모두 자연스러운 한국어로 작성한다. 일본어나 한국어·일본어 혼합 문장을 쓰지 않는다. searchQuery와 generationPrompt만 영어로 작성하고 근거 ID와 고유한 제품명은 보존한다.\n'+prompt;
    const evidenceStart = prompt.indexOf('evidence는 제목·요약');
    const evidenceEnd = prompt.indexOf('visual.type은', evidenceStart);
    if (evidenceStart >= 0 && evidenceEnd >= 0) prompt = prompt.slice(0, evidenceStart)
      + 'evidence는 주장을 직접 뒷받침하는 sources.evidence의 id 문자열 배열이다. 제목·요약과 각 슬라이드에 해당하는 근거 ID를 선택한다. 원문 인용문을 다시 쓰지 않는다. 존재하는 ID만 사용하고 각 슬라이드 sourceIds에는 해당 근거의 출처 ID를 넣는다. 근거에 없는 사실이나 숫자는 넣지 않는다.\\n'.replace('\\n', '\n')
      + prompt.slice(evidenceEnd);
    prompt = prompt.replace('sources의 title/text', 'sources의 title/evidence')
      .replace('같은 assetKey, description, generationPrompt를 정확히 재사용한다.', '같은 assetKey와 generationPrompt를 정확히 재사용한다. description은 슬라이드별로 달라도 된다.');
    code = code.slice(0, startPrompt) + JSON.stringify(prompt) + code.slice(endPrompt);
  }
  const imageError = code.indexOf("throw new Error('이미지 계획 필드 오류');");
  if (imageError >= 0) {
    const imageStart = code.lastIndexOf('      if (!/^[a-z]', imageError);
    if (imageStart < 0) throw new Error('Cannot locate image plan validation');
    code = code.slice(0, imageStart) + '      normalizeImagePlan(x.visual, x.sequence);'
      + code.slice(imageError + "throw new Error('이미지 계획 필드 오류');".length);
  }
  code = code.replace(
    "if (existing && (existing.description!==x.visual.description || existing.generationPrompt!==x.visual.generationPrompt))\n        throw new Error('같은 assetKey는 동일한 이미지 용도여야 합니다.');\n      assets.set(k,",
    "if (existing && existing.generationPrompt!==x.visual.generationPrompt)\n        throw new Error('슬라이드 '+x.sequence+': 같은 assetKey의 generationPrompt가 다릅니다.');\n      if (!existing) assets.set(k,");
  const start = code.indexOf('    const quoted = clean(x.evidence');
  if (start >= 0) {
    const stop = code.indexOf("    if (['photo','illustration'].includes(x.visual.type))", start);
    if (stop < 0) throw new Error('Cannot locate slide validation boundary');
    code = code.slice(0, start) + `    const semanticData = x.visual.type==='diagram' ? x.visual.data.nodes.map(n=>n.label).join(' ')
      : x.visual.type==='chart' ? x.visual.data.points.map(p=>p.label+' '+p.value+' '+x.visual.data.unit).join(' ')
      : x.visual.data ? JSON.stringify(x.visual.data) : '';
    assertSupportedNumbers([x.title,x.content,semanticData].join(' '), x.evidence.map(q=>q.quote).join(' '), '슬라이드 '+x.sequence);
` + code.slice(stop);
  }
  const summaryStart = code.indexOf('  const summaryNumbers =');
  if (summaryStart >= 0) {
    const stop = code.indexOf('  return {draft:d,assets:[...assets.values()]};', summaryStart);
    if (stop < 0) throw new Error('Cannot locate summary validation boundary');
    code = code.slice(0, summaryStart)
      + "  assertSupportedNumbers([d.title,d.summary].join(' '), d.evidence.map(q=>q.quote).join(' '), '제목/요약');\n"
      + code.slice(stop);
  }
  node.parameters.jsCode = code;
  updated++;
}
if (!updated) throw new Error('No validation helpers found');
fs.writeFileSync(file, JSON.stringify(wf, null, 2) + '\n');
console.log(`Updated validation helpers in ${updated} nodes`);
