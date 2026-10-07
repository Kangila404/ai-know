// A script-level constraint, not a guarantee of semantic translation quality.
// Keep Latin product names and English retrieval/generation fields untouched.
export function koreanPattern(allowEmpty = false) {
  const text = '[^ぁ-ヿ]*[가-힣][^ぁ-ヿ]*';
  return allowEmpty ? '^(?:|' + text + ')$' : '^' + text + '$';
}

export function koreanDraftSchema(schema) {
  const next = JSON.parse(JSON.stringify(schema));
  const korean = (field, empty = false) => ({...field, pattern:koreanPattern(empty),
    description:'Write natural Korean (ko-KR) with Hangul. Do not write Japanese or mix Japanese sentences into Korean. Keep Latin product names when needed.'
      + (empty ? ' An empty string is allowed only for unused fields when status is skip, or reason when status is ready.' : '')});
  for(const key of ['title','summary','reason'])next.properties[key]=korean(next.properties[key],true);
  for(const key of ['coreMessage','readerNeeds','sequenceReason'])
    next.properties.plan.properties[key]=korean(next.properties.plan.properties[key],true);
  const slide=next.properties.slides.items.properties;
  for(const key of ['title','content'])slide[key]=korean(slide[key]);
  slide.visual.properties.description=korean(slide.visual.properties.description);
  return next;
}

export function assertKoreanText(value, field) {
  if(typeof value!=='string' || !new RegExp(koreanPattern()).test(value))
    throw new Error(field+'의 한국어 형식 오류: 한글 문장이 필요하며 일본어 문장을 섞을 수 없습니다.');
}

export function validateKoreanDraft(draft) {
  if(draft.status==='skip'){assertKoreanText(draft.reason,'건너뛰기 사유');return;}
  assertKoreanText(draft.title,'제목');assertKoreanText(draft.summary,'요약');
  for(const key of ['coreMessage','readerNeeds','sequenceReason'])assertKoreanText(draft.plan?.[key],'구성 계획 '+key);
  for(const slide of draft.slides || []){
    assertKoreanText(slide.title,'슬라이드 '+slide.sequence+' 제목');
    assertKoreanText(slide.content,'슬라이드 '+slide.sequence+' 본문');
    assertKoreanText(slide.visual?.description,'슬라이드 '+slide.sequence+' 시각 설명');
  }
}
