// This pure function is also embedded in the n8n Code node.
export function toImportRequest(draft, media = {}) {
  const requiredText = (value, max, field) => {
    if (typeof value !== 'string' || !value.trim() || value.length > max) throw new Error(`Invalid ${field}`);
    return value.trim();
  };
  const https = (value, field) => {
    if (typeof value !== 'string' || value.length > 2048 || !/^https:\/\/[^\s]+$/i.test(value)) throw new Error(`Invalid HTTPS ${field}`);
    return value;
  };
  const image = value => {
    if (!value) return null;
    if (!value.url) throw new Error('Image needs a permanent HTTPS URL before server import. Upload generated binary images first.');
    const origin = value.origin === 'sourced' ? 'SOURCE' : String(value.origin).toUpperCase();
    if (!['SOURCE', 'GENERATED'].includes(origin)) throw new Error('Invalid image origin');
    const sourceUrl = value.sourceUrl ?? value.sourcePageUrl ?? null;
    const credit = origin === 'SOURCE'
      ? [value.creator, value.credit, value.license, value.licenseUrl].filter(Boolean).join(' / ')
      : (value.credit ?? null);
    if (origin === 'SOURCE') {
      https(sourceUrl, 'image source');
      requiredText(credit, 500, 'image attribution');
    }
    const localPrefix = media.mode === 'local' && /^http:\/\/[^\s]+$/.test(media.publicBaseUrl ?? '')
      ? media.publicBaseUrl.replace(/\/+$/, '') + '/' : null;
    const localUrl = origin === 'GENERATED' && localPrefix && value.url.startsWith(localPrefix)
      && /^[a-f0-9]{64}\.png$/.test(value.url.slice(localPrefix.length));
    return { url: localUrl ? value.url : https(value.url, 'image'), origin, sourceUrl, credit };
  };
  const date = Date.parse(draft.publishedAt);
  if (!Number.isFinite(date)) throw new Error('Invalid publishedAt');
  if (!Array.isArray(draft.slides) || draft.slides.length < 1 || draft.slides.length > 10) throw new Error('Expected 1-10 slides');
  return {
    sourceTitle: requiredText(draft.sourceTitle, 500, 'sourceTitle'),
    sourceUrl: https(draft.sourceUrl, 'sourceUrl'), publishedAt: new Date(date).toISOString(),
    title: requiredText(draft.title, 200, 'title'), summary: requiredText(draft.summary, 5000, 'summary'),
    keyPoints: draft.keyPoints ?? [], titleImage: image(draft.titleImage),
    slides: draft.slides.map((slide, index) => {
      if (slide.sequence !== index + 1) throw new Error('Slides must be sequential');
      return {
        sequence: slide.sequence, title: requiredText(slide.title, 200, 'slide title'),
        content: requiredText(slide.content, 5000, 'slide content'),
        layout: slide.layout ?? slide.visual?.type ?? null,
        image: image(slide.image ?? slide.visual?.image),
      };
    }),
  };
}

export function ingestionNodes(url = 'http://host.docker.internal:8080/internal/v1/card-news/import', media = {}) {
  return [
    {
      id: 'aiknow-import-transform', name: '서버 요청 변환', type: 'n8n-nodes-base.code', typeVersion: 2,
      position: [2496, 464],
      parameters: { jsCode: `${toImportRequest.toString()}\nreturn $input.all().map((item, index) => ({ json: { request: toImportRequest(item.json, ${JSON.stringify(media)}), reviewWarnings: item.json.validation?.warnings ?? [] }, pairedItem: { item: index } }));` },
    },
    {
      id: 'aiknow-server-import', name: '서버 검수 대기 저장', type: 'n8n-nodes-base.httpRequest', typeVersion: 4.2,
      position: [2720, 464],
      parameters: {
        method: 'POST', url, authentication: 'genericCredentialType', genericAuthType: 'httpHeaderAuth',
        sendBody: true, specifyBody: 'json', jsonBody: '={{ JSON.stringify($json.request) }}',
        options: { timeout: 30000, redirect: { redirect: { followRedirects: false } },
          response: { response: { fullResponse: true, responseFormat: 'json' } } },
      },
      credentials: { httpHeaderAuth: { id: 'aiknow-local-ingest', name: 'AI_KNOW Ingestion' } },
      retryOnFail: false,
    },
    {
      id: 'aiknow-import-receipt', name: '저장 확인 후 URL 기록', type: 'n8n-nodes-base.code', typeVersion: 2,
      position: [2944, 464],
      parameters: { jsCode: `const response = $input.first().json;
const receipt = response.body;
if (response.statusCode !== 202 || !Number.isInteger(receipt?.id) || receipt.id < 1
    || !['PENDING', 'APPROVED', 'DENIED'].includes(receipt.status)) {
  throw new Error('Server import receipt is invalid. Processing history was not updated.');
}
const mapped = $('서버 요청 변환').first().json;
const draft = mapped.request;
if ($execution.mode !== 'manual') {
  const state = $getWorkflowStaticData('global');
  const processed = new Set(Array.isArray(state.aiKnowProcessedUrls) ? state.aiKnowProcessedUrls : []);
  processed.add(draft.sourceUrl);
  state.aiKnowProcessedUrls = [...processed];
}
return [{ json: { sourceUrl: draft.sourceUrl, submissionId: receipt.id,
  status: receipt.status, cardNewsId: receipt.cardNewsId, httpStatus: response.statusCode, reviewWarnings: mapped.reviewWarnings ?? [],
  historyPersistence: $execution.mode === 'manual' ? 'manual_not_persisted' : 'after_server_acceptance' } }];` },
    },
  ];
}

export function ingestionConnections(from) {
  const names = [from, '서버 요청 변환', '서버 검수 대기 저장', '저장 확인 후 URL 기록'];
  return Object.fromEntries(names.slice(0, -1).map((name, i) =>
    [name, { main: [[{ node: names[i + 1], type: 'main', index: 0 }]] }]));
}
