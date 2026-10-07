import { koreanDraftSchema } from './korean-draft.mjs';
import { draftLimits, applyDraftLimits } from './draft-contract.mjs';

// Models select IDs. Quotation text is always copied locally from the source.
export function evidenceCatalog(docs) {
  const catalog = [];
  for (const doc of docs) {
    const pieces = [doc.title, ...String(doc.text ?? '').split(/(?<=[.!?])\s+(?=[A-Z“"‘])/u)];
    for (const text of pieces) {
      const quote = String(text ?? '').trim();
      if (quote.length < 12) continue;
      catalog.push({ id: `${doc.id}:q${catalog.length + 1}`, sourceId: doc.id, quote });
      if (catalog.length >= draftLimits().evidence) return catalog;
    }
  }
  return catalog;
}

export function resolveEvidence(draft, catalog = []) {
  const byId = new Map(catalog.map(q => [q.id, q]));
  const resolve = (refs, label) => {
    if (!Array.isArray(refs)) throw new Error(`${label}: 근거 배열이 필요합니다.`);
    return refs.map(ref => {
      // Historical responses retain their strict literal-quote checks.
      if (ref && typeof ref === 'object' && !Array.isArray(ref)) return ref;
      const source = typeof ref === 'string' ? byId.get(ref) : null;
      if (!source) throw new Error(`${label}: 원문에 없는 근거 ID ${String(ref).slice(0, 80)}`);
      return { sourceId: source.sourceId, quote: source.quote };
    });
  };
  draft.evidence = resolve(draft.evidence, '제목/요약');
  for (const slide of draft.slides ?? []) slide.evidence = resolve(slide.evidence, `슬라이드 ${slide.sequence}`);
  return draft;
}

export function evidenceRequest(state, schema) {
  const catalog = evidenceCatalog(state.docs);
  if (!catalog.length) throw new Error('근거 문장이 없습니다.');
  state.evidenceCatalog = catalog;
  const next = applyDraftLimits(koreanDraftSchema(schema));
  const refs = { type: 'array', minItems:0, maxItems:draftLimits().evidence, items: { type: 'string', enum: catalog.map(q => q.id) } };
  next.properties.evidence = refs;
  next.properties.slides.items.properties.evidence = JSON.parse(JSON.stringify(refs));
  next.properties.slides.items.properties.evidence.minItems=1;
  if (next.properties.slides.items.properties.sourceIds)
    next.properties.slides.items.properties.sourceIds.items = { type: 'string', enum: state.docs.map(d => d.id) };
  return { schema: next, sources: state.docs.map(({text, ...doc}) => ({ ...doc,
    evidence: catalog.filter(q => q.sourceId === doc.id).map(({id, quote}) => ({id, quote})) })) };
}
