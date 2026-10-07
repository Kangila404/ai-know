// Embedded in Code nodes: compare numeric values after notation conversion.
// This checks numeric evidence presence, not the meaning/attribution of a claim.
export function numericValues(input) {
  const words = { zero: 0, one: 1, two: 2, three: 3, four: 4, five: 5, six: 6,
    seven: 7, eight: 8, nine: 9, ten: 10, eleven: 11, twelve: 12, thirteen: 13,
    fourteen: 14, fifteen: 15, sixteen: 16, seventeen: 17, eighteen: 18, nineteen: 19,
    twenty: 20, thirty: 30, forty: 40, fifty: 50, sixty: 60, seventy: 70, eighty: 80, ninety: 90 };
  const months = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august',
    'september', 'october', 'november', 'december'];
  let text = String(input ?? '').replace(/\b(January|February|March|April|May|June|July|August|September|October|November|December)\b/g,
    word => `${months.indexOf(word.toLowerCase()) + 1}월`);
  // An indefinite article expresses one only before a measurement/scale unit.
  // Do not turn articles in ordinary prose ("a platform", "a few years") into evidence for 1.
  const measuredUnit = '(?:year|month|week|day|hour|minute|second|decade|century|hundred|thousand|million|billion|trillion)';
  text = text.replace(new RegExp(`\\bhalf (?:a|an) (?=${measuredUnit}\\b)`, 'gi'), '0.5 ')
    .replace(new RegExp(`\\b(?:a|an) (?=${measuredUnit}\\b)`, 'gi'), '1 ');
  const wordPattern = new RegExp(`\\b(${Object.keys(words).join('|')})(?:[- ](one|two|three|four|five|six|seven|eight|nine))?\\b`, 'gi');
  text = text.replace(wordPattern, (_, first, second) => {
    const value = words[first.toLowerCase()];
    return second && value >= 20 ? String(value + words[second.toLowerCase()])
      : second ? `${value} ${words[second.toLowerCase()]}` : String(value);
  });
  const scales = { hundred: 100, thousand: 1000, million: 1e6, billion: 1e9, trillion: 1e12,
    '십': 10, '백': 100, '천': 1000, '만': 1e4, '억': 1e8, '조': 1e12 };
  const number = String.raw`\d+(?:,\d{3})*(?:\.\d+)?`;
  const scale = String.raw`(?:hundred|thousand|million|billion|trillion)\b|[십백천만억조]`;
  const quantity = new RegExp(`${number}(?:\\s*(?:${scale})(?:\\s*${number})?)*`, 'gi');
  return [...text.matchAll(quantity)].map(match => {
    const parts = match[0].replace(/,/g, '').match(/\d+(?:\.\d+)?|hundred|thousand|million|billion|trillion|[십백천만억조]/gi);
    let total = 0, group = 0, pending = null;
    for (const part of parts) {
      const multiplier = scales[part.toLowerCase()];
      if (!multiplier) { pending = Number(part); continue; }
      if (multiplier < 10000) { group += (pending ?? 1) * multiplier; pending = null; }
      else { total += (group + (pending ?? 0) || 1) * multiplier; group = 0; pending = null; }
    }
    return String(total + group + (pending ?? 0));
  });
}

export function assertSupportedNumbers(text, evidence, context) {
  const supported = new Set(numericValues(evidence));
  const missing = [...new Set(numericValues(text).filter(value => !supported.has(value)))];
  if (missing.length) throw new Error(`${context}: 인용 근거에 없는 수치가 있습니다 (${missing.join(', ')}).`);
}

export function parseVisualJson(raw) {
  try { return JSON.parse(raw); }
  catch {
    // Some model responses encode the JSON object's quotes twice. Decode one
    // JSON string layer only; the caller still validates the complete schema.
    if (typeof raw !== 'string' || !/^\s*\{\\"/.test(raw)) throw new Error('시각 자료 데이터 JSON 오류');
    try { return JSON.parse(JSON.parse('"' + raw + '"')); }
    catch { throw new Error('시각 자료 데이터 JSON 오류'); }
  }
}

// The model schema permits a null searchQuery. Derive retrieval keywords from
// the validated asset key; do not change article text or invent image evidence.
export function normalizeImagePlan(visual, sequence) {
  const invalid = field => { throw new Error(`슬라이드 ${sequence} 이미지 계획: ${field} 오류`); };
  if (typeof visual.assetKey !== 'string' || !/^[a-z][a-z0-9_-]{0,39}$/.test(visual.assetKey)) invalid('assetKey (영문 소문자로 시작하는 1~40자)');
  if (visual.searchQuery != null && typeof visual.searchQuery !== 'string') invalid('searchQuery (문자열 또는 null)');
  let query = visual.searchQuery?.trim();
  if (!query) query = visual.assetKey.replace(/[_-]+/g, ' ').trim();
  if (query.length > 180) invalid('searchQuery (최대 180자)');
  if (typeof visual.generationPrompt !== 'string' || !visual.generationPrompt.trim()
      || visual.generationPrompt.length > 1200) invalid('generationPrompt (1~1200자)');
  visual.searchQuery = query;
  return visual;
}
