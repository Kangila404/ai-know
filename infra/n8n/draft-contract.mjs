// These limits are shared by the model schema and runtime validator.
export function draftLimits() {
  return {evidence:200, minSlides:3, maxSlides:7, maxAssets:5,
    title:200, summary:5000, slideTitle:120, slideContent:1200, description:500, plan:1000};
}

export function applyDraftLimits(schema) {
  const s=JSON.parse(JSON.stringify(schema)),l=draftLimits(),p=s.properties;
  p.title.maxLength=l.title;p.summary.maxLength=l.summary;
  for(const key of ['coreMessage','readerNeeds','sequenceReason'])p.plan.properties[key].maxLength=l.plan;
  // An empty slides/evidence array is valid for status=skip; ready is checked at runtime.
  p.slides.minItems=0;p.slides.maxItems=l.maxSlides;
  const slide=p.slides.items.properties;
  slide.sequence.minimum=1;slide.sequence.maximum=l.maxSlides;
  slide.title.maxLength=l.slideTitle;slide.content.maxLength=l.slideContent;
  slide.visual.properties.description.maxLength=l.description;
  slide.visual.properties.assetKey.pattern='^[a-z][a-z0-9_]{0,39}$';
  slide.visual.properties.searchQuery.maxLength=180;
  slide.visual.properties.generationPrompt.maxLength=1200;
  return s;
}

export function assertEvidence(quotes, docs, allowedIds, context) {
  const clean=v=>String(v??'').replace(/\s+/g,' ').trim();
  if(!Array.isArray(quotes)||!quotes.length)throw new Error(context+': 근거 배열이 비어 있습니다.');
  if(quotes.length>draftLimits().evidence)throw new Error(context+': 근거 개수 상한 초과 ('+quotes.length+').');
  for(const [index,q]of quotes.entries()){
    const doc=docs.find(d=>d.id===q?.sourceId);
    if(!doc)throw new Error(context+': 존재하지 않는 출처 ID ('+String(q?.sourceId)+').');
    if(allowedIds&&!allowedIds.includes(q.sourceId))throw new Error(context+': 슬라이드 출처 목록에 없는 근거 ('+q.sourceId+').');
    if(clean(q.quote).length<12)throw new Error(context+': 근거 '+(index+1)+'의 인용문이 너무 짧습니다.');
    if(!clean(doc.title+' '+doc.text).includes(clean(q.quote)))
      throw new Error(context+': 근거 '+(index+1)+'의 인용문이 원문과 다릅니다.');
  }
}
