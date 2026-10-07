// An unavailable decorative image must not discard a valid review draft.
export function fallbackImageToText(state, asset, reason) {
  if (asset.image) throw new Error('Cannot replace an acquired image with a fallback');
  const affected = [];
  for (const slide of state.draft.slides) {
    if (!['photo', 'illustration'].includes(slide.visual.type) || slide.visual.assetKey !== asset.key) continue;
    const originalType = slide.visual.type;
    slide.visual = { type: 'text', description: '이미지 없이 제목과 본문으로 구성한 검수용 슬라이드',
      dataJson: null, data: null, assetKey: null, searchQuery: null, generationPrompt: null };
    affected.push({ sequence: slide.sequence, originalType });
  }
  if (!affected.length) throw new Error('Missing image is not referenced by any slide');
  const warning = { code: 'IMAGE_UNAVAILABLE', assetKey: asset.key,
    slides: affected.map(slide => slide.sequence), reason, fallback: 'text' };
  state.visualWarnings ??= [];
  state.visualWarnings.push(warning);
  state.audit.push({ event: 'image_text_fallback', detail: warning });
  asset.fallback = 'text';
}
