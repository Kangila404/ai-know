import fs from 'node:fs';
import { fallbackImageToText } from './image-fallback.mjs';

const file = process.argv[2] ?? new URL('./workflows/ai-know-news-draft.json', import.meta.url);
const wf = JSON.parse(fs.readFileSync(file, 'utf8'));
const controller = wf.nodes.find(node => node.name === '작업 결정');
const final = wf.nodes.find(node => node.name === '초안 검증 완료');
if (!controller || !final) throw new Error('Expected workflow nodes were not found');
let code = controller.parameters.jsCode;
const old = "if (!s.config.allowImageGeneration) {fail(s,'필요한 이미지를 확보하지 못했고 이미지 생성이 꺼져 있습니다.');continue;}";
if (code.includes(old)) {
  code = code.replace(old, "if (!s.config.allowImageGeneration) {fallbackImageToText(s,a,'사용 조건과 관련성을 충족하는 이미지를 확보하지 못했고 이미지 생성이 꺼져 있습니다.');s.assetIndex++;continue;}");
}
const begin = '// BEGIN IMAGE FALLBACK HELPER';
const end = '// END IMAGE FALLBACK HELPER';
const helper = `${begin}\n${fallbackImageToText.toString()}\n${end}\n`;
if (code.includes(begin)) {
  const start = code.indexOf(begin), stop = code.indexOf(end, start);
  if (stop < 0) throw new Error('Incomplete fallback helper');
  code = code.slice(0, start) + helper + code.slice(stop + end.length + 1);
} else {
  code = code.replace('const item=$input.first(), s=clone(item.json);', helper + 'const item=$input.first(), s=clone(item.json);');
}
if (!code.includes('fallbackImageToText(s,a,')) throw new Error('Controller fallback was not connected');
controller.parameters.jsCode = code;
final.parameters.jsCode = final.parameters.jsCode.replace("'numeric_presence','required_images'", "'numeric_presence','image_policy'");
if (!final.parameters.jsCode.includes('warnings:s.visualWarnings')) {
  final.parameters.jsCode = final.parameters.jsCode.replace("validation:{status:'draft_ready',reviewRequired:true,", "validation:{status:'draft_ready',reviewRequired:true,warnings:s.visualWarnings||[],");
}
fs.writeFileSync(file, JSON.stringify(wf, null, 2) + '\n');
console.log('Missing-image fallback and review warnings connected');
