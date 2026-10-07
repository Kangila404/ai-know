// Pure helpers embedded in n8n Code nodes. No paid requests or dynamic code here.
export function imageText(value) {
  return String(value ?? '').replace(/<[^>]*>/g, ' ').replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, ' ').trim();
}

export function imageHttps(value, kind) {
  const url = String(value ?? '').trim();
  const match = /^https:\/\/([a-z0-9.-]+)(\/[^\s\\<>"']*)?$/i.exec(url);
  if (!match || url.length > 2000) return null;
  const host = match[1].toLowerCase();
  const allowed = kind === 'page'
    ? ['commons.wikimedia.org', 'www.flickr.com', 'flickr.com', 'stocksnap.io', 'www.rawpixel.com', 'rawpixel.com']
    : ['upload.wikimedia.org', 'live.staticflickr.com', 'cdn.stocksnap.io', 'images.rawpixel.com'];
  if (!allowed.includes(host)) return null;
  return 'https://' + host + (match[2] || '/');
}

export function imageLicense(value) {
  const url = String(value ?? '').replace(/^http:/, 'https:');
  const match = /^https:\/\/creativecommons\.org\/(licenses\/by\/(?:1\.0|2\.0|2\.5|3\.0|4\.0)|publicdomain\/(?:zero|mark)\/1\.0)(?:\/(?:deed(?:\.[a-z_-]+)?|legalcode(?:\.[a-z_-]+)?))?\/?$/i.exec(url);
  return match ? 'https://creativecommons.org/' + match[1].toLowerCase() + '/' : null;
}

export function imageSearchPlan(asset) {
  // A specific subject and a broader illustrative concept, both supplied by the draft.
  const queries = [...new Set(String(asset.searchQuery || asset.key.replace(/_/g, ' '))
    .split('|').map(q => q.replace(/[^a-z0-9 .'-]/gi, ' ').replace(/\s+/g, ' ').trim())
    .filter(Boolean))].slice(0, 2);
  return queries.flatMap(query => ['commons', 'openverse'].map(provider => {
    const params = provider === 'commons'
      ? { action: 'query', format: 'json', formatversion: '2', generator: 'search', gsrnamespace: '6',
        gsrsearch: query, gsrlimit: '12', prop: 'imageinfo', iiprop: 'url|mime|size|extmetadata', iiextmetadatalanguage: 'en' }
      : { q: query, license: 'by,cc0,pdm', page_size: '12', mature: 'false' };
    const base = provider === 'commons' ? 'https://commons.wikimedia.org/w/api.php' : 'https://api.openverse.org/v1/images/';
    return { provider, query, url: base + '?' + Object.entries(params).map(([k,v]) => encodeURIComponent(k)+'='+encodeURIComponent(v)).join('&') };
  }));
}

export function imageCandidates(body, provider) {
  const rows = provider === 'commons'
    ? Object.values(body?.query?.pages || {}).sort((a,b) => (a.index ?? 999) - (b.index ?? 999))
    : (Array.isArray(body?.results) ? body.results : []);
  const candidates = [];
  for (const row of rows.slice(0, 12)) {
    let value;
    if (provider === 'commons') {
      const i = row.imageinfo?.[0], m = i?.extmetadata || {};
      if (!i || !['image/jpeg','image/png','image/webp'].includes(i.mime) || imageText(m.Restrictions?.value)) continue;
      value = { candidateId: 'commons:' + row.pageid, title: row.title, description: m.ImageDescription?.value,
        creator: m.Artist?.value, license: m.LicenseShortName?.value, licenseUrl: m.LicenseUrl?.value,
        sourcePageUrl: i.descriptionurl, url: i.url, width: i.width, height: i.height };
    } else {
      if (row.mature === true || !['by','cc0','pdm'].includes(row.license)) continue;
      if (row.filetype && !['jpg','jpeg','png','webp'].includes(row.filetype.toLowerCase())) continue;
      value = { candidateId: 'openverse:' + row.id, title: row.title,
        description: [row.title, ...(row.tags || []).slice(0,12).map(t => t.name)].join(' '), creator: row.creator,
        license: row.license === 'by' ? 'CC BY ' + row.license_version : row.license === 'cc0' ? 'CC0 1.0' : 'Public Domain Mark 1.0',
        licenseUrl: row.license_url, sourcePageUrl: row.foreign_landing_url, url: row.url, width: row.width, height: row.height };
    }
    const licenseUrl = imageLicense(value.licenseUrl), sourcePageUrl = imageHttps(value.sourcePageUrl, 'page'), url = imageHttps(value.url, 'image');
    const creator = imageText(value.creator), license = imageText(value.license);
    if (!licenseUrl || !sourcePageUrl || !url || !creator || !license || creator.length > 240
      || /\b(?:NC|ND|SA)\b/i.test(license) || /\b(?:unknown|anonymous|not provided)\b/i.test(creator)) continue;
    if ((value.width && value.width < 480) || (value.height && value.height < 320)) continue;
    candidates.push({ ...value, title: imageText(value.title).slice(0,140), description: imageText(value.description).slice(0,320),
      creator, license, licenseUrl, sourcePageUrl, url, provider, credit: '', attribution: '' });
  }
  return candidates;
}

export function mergeImageCandidates(existing, added) {
  const found = new Map();
  for (const c of [...existing, ...added]) {
    const key = c.url.split('?')[0];
    if (!found.has(key)) found.set(key, c);
  }
  return [...found.values()].slice(0,48);
}

export function imageShortlist(asset) {
  // Keep a mix of providers and queries, rather than allowing the first query to occupy every slot.
  const groups = new Map();
  for (const c of asset.candidates) {
    const key = c.provider + ':' + (c.searchQuery || '');
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(c);
  }
  const results = [];
  for (let i=0; results.length<8 && i<12; i++) {
    for (const group of groups.values()) if (group[i] && results.length<8) results.push(group[i]);
  }
  return results;
}

export function imageSelectionPayload(state) {
  return {articleTitle: state.draft.title.slice(0,200), articleSummary: state.draft.summary.slice(0,1000),
    assets: state.assets.map(a => ({assetKey:a.key,purpose:a.description.slice(0,240),
      candidates:imageShortlist(a).map(c=>({candidateId:c.candidateId,title:c.title.slice(0,120),description:c.description.slice(0,200)}))}))};
}

export function imageSelectionSchema(state, schema) {
  const next=JSON.parse(JSON.stringify(schema));
  const template=next.properties.selections.items;
  next.properties.selections.minItems=state.assets.length;
  next.properties.selections.maxItems=state.assets.length;
  next.properties.selections.items={anyOf:state.assets.map(asset=>{
    const choice=JSON.parse(JSON.stringify(template)),ids=imageShortlist(asset).map(c=>c.candidateId);
    choice.properties.assetKey.enum=[asset.key];
    choice.properties.candidateId.enum=[...ids,null];
    choice.properties.alternativeCandidateIds.maxItems=Math.min(2,ids.length);
    if(ids.length)choice.properties.alternativeCandidateIds.items.enum=ids;
    return choice;
  })};
  return next;
}

export function applyImageChoices(state, result) {
  if (!Array.isArray(result.selections) || result.selections.length !== state.assets.length) throw new Error('이미지 선택 개수 오류');
  const seen = new Set();
  for (const choice of result.selections) {
    const asset = state.assets.find(a => a.key === choice.assetKey);
    if (!asset || seen.has(asset.key)) throw new Error('이미지 선택 키 오류');
    seen.add(asset.key);
    const ids = [choice.candidateId, ...(choice.alternativeCandidateIds || [])].filter(x => x !== null);
    const allowed = new Map(imageShortlist(asset).map(c => [c.candidateId,c]));
    if (ids.length>3 || new Set(ids).size!==ids.length || ids.some(id => !allowed.has(id))) throw new Error('검색 후보에 없는 이미지 또는 중복 선택');
    asset.selectionQueue = choice.confidence === 'high' && imageText(choice.reason) && choice.candidateId !== null
      ? ids.map(id => allowed.get(id)) : [];
    asset.selected = asset.selectionQueue.shift() || null;
    asset.pageChecked = false;
    state.audit.push({event:'image_selection',detail:{assetKey:asset.key,candidateId:asset.selected?.candidateId || null,
      alternatives:asset.selectionQueue.length,reason:choice.reason}});
  }
}

export function rejectImageChoice(asset) {
  asset.selected = asset.selectionQueue?.shift() || null;
  asset.pageChecked = false;
}
