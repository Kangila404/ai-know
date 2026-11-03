// Editorial body images are mandatory and unique. Covers may reuse slide 1.
export function requiredSlidePlans(draft) {
  return draft.slides.map(slide => {
    const visual = slide.visual;
    const prompt = [visual.generationPrompt || visual.description, slide.title, slide.content].filter(Boolean).join('\n');
    // Also upgrade old cached text/chart plans: mobile clients render raster visuals, not dataJson.
    slide.visual = {...visual, type: 'illustration', assetKey: `slide_${slide.sequence}`,
      searchQuery: visual.searchQuery || 'artificial intelligence | technology research',
      generationPrompt: prompt.slice(0, 1200), dataJson: null, data: null};
    return {key:slide.visual.assetKey, description:visual.description, searchQuery:slide.visual.searchQuery,
      generationPrompt:slide.visual.generationPrompt, candidates:[], selected:null, image:null};
  });
}

export function imageIdentity(image) {
  // Prefer the source page: Commons thumbnails and originals are the same image.
  return String(image?.sourcePageUrl || image?.sourceUrl || image?.url || image?.storageKey || image?.binaryProperty || '')
    .replace(/[?#].*$/, '').replace(/^http:/, 'https:');
}

export function assertCompleteImages(slides) {
  const used = new Set();
  for (const slide of slides) {
    if (!String(slide.title || '').trim() || !String(slide.content || '').trim()) throw new Error('빈 슬라이드는 저장할 수 없습니다.');
    const key = imageIdentity(slide.image || slide.visual?.image);
    if (!key) throw new Error(`슬라이드 ${slide.sequence}: 이미지 누락. 복구 후 저장하세요.`);
    if (used.has(key)) throw new Error(`슬라이드 ${slide.sequence}: 본문 이미지 중복. 다른 이미지가 필요합니다.`);
    used.add(key);
  }
}
