// Pure helpers are embedded in Code nodes; binary data stays in n8n's binary store.
export function prepareImageUploads(items) {
  if (items.length !== 1) throw new Error('Expected exactly one news draft');
  const item = items[0], draft = item.json;
  const images = [draft.titleImage, ...(draft.slides ?? []).map(s => s.image ?? s.visual?.image)].filter(Boolean);
  const properties = [...new Set(images.filter(i => i.origin?.toLowerCase() === 'generated' && !i.url)
    .map(i => i.binaryProperty))];
  if (!properties.length) return [{ json: { draft, uploadRequired: false }, pairedItem: { item: 0 } }];
  return properties.map(property => {
    if (!property || !item.binary?.[property]) throw new Error('Generated image binary is missing');
    return { json: { draft, uploadRequired: true, binaryProperty: property },
      binary: { file: item.binary[property] }, pairedItem: { item: 0 } };
  });
}

export function applyUploadedImages(prepared, responses, media = {}) {
  if (!prepared.length) throw new Error('Missing upload plan');
  const draft = JSON.parse(JSON.stringify(prepared[0].json.draft));
  const images = [draft.titleImage, ...(draft.slides ?? []).map(s => s.image ?? s.visual?.image)].filter(Boolean);
  if (prepared[0].json.uploadRequired) {
    if (responses.length !== prepared.length) throw new Error('Some image uploads are missing');
    const seen = new Set();
    for (const [index, response] of responses.entries()) {
      const linked = response.pairedItem?.item ?? index;
      if (!Number.isInteger(linked) || !prepared[linked] || seen.has(linked)) throw new Error('Invalid upload pairing');
      seen.add(linked);
      const receipt = response.json.body;
      const localPrefix = media.mode === 'local' && /^http:\/\/[^\s]+$/.test(media.publicBaseUrl ?? '')
        ? media.publicBaseUrl.replace(/\/+$/, '') + '/' : null;
      const localUrl = localPrefix && typeof receipt?.url === 'string' && receipt.url.startsWith(localPrefix)
        && /^[a-f0-9]{64}\.png$/.test(receipt.url.slice(localPrefix.length));
      if (response.json.statusCode !== 201 || receipt?.contentType !== 'image/png'
        || !/^[a-f0-9]{64}\.png$/.test(receipt?.key ?? '')
        || typeof receipt.url !== 'string' || (!/^https:\/\/[^\s]+$/.test(receipt.url) && !localUrl)
        || (media.publicBaseUrl && receipt.url !== media.publicBaseUrl.replace(/\/+$/, '') + '/' + receipt.key)
        || !receipt.url.endsWith('/' + receipt.key) || !(receipt.size > 0))
        throw new Error('Image upload receipt is invalid; draft was not imported');
      for (const image of images.filter(i => i.origin?.toLowerCase() === 'generated' && !i.url
        && i.binaryProperty === prepared[linked].json.binaryProperty)) {
        image.url = receipt.url;
        image.urlKind = 'permanent';
        image.credit = 'AI 생성 이미지';
        image.storageKey = receipt.key;
      }
    }
  }
  if (images.some(i => i.origin?.toLowerCase() === 'generated' && !i.url))
    throw new Error('Generated image has no permanent URL');
  if (draft.run) draft.run.imageStorage = 'Generated images uploaded to the configured media share before import';
  return [{ json: draft, pairedItem: { item: 0 } }];
}

export function imageUploadNodes(baseUrl, credentials, media = {}) {
  const auth = { authentication: 'genericCredentialType', genericAuthType: 'httpHeaderAuth' };
  const options = { timeout: 60000, redirect: { redirect: { followRedirects: false } },
    response: { response: { fullResponse: true, responseFormat: 'json' } } };
  const code = (id, name, position, jsCode) => ({ id, name, position, type: 'n8n-nodes-base.code', typeVersion: 2, parameters: { jsCode } });
  const condition = (id, name, position, expression) => ({ id, name, position, type: 'n8n-nodes-base.if', typeVersion: 2.2,
    parameters: { conditions: { options: { caseSensitive: true, leftValue: '', typeValidation: 'strict', version: 2 },
      conditions: [{ id: id + '-condition', leftValue: expression, rightValue: true,
        operator: { type: 'boolean', operation: 'true', singleValue: true } }], combinator: 'and' }, options: {} } });
  return [
    condition('aiknow-storage-check-needed', '이미지 생성 저장소 점검 여부', [200, -304], '={{ $json.config.allowImageGeneration && !$json.config.dryRun }}'),
    { id: 'aiknow-storage-check', name: '이미지 저장소 연결 점검', position: [424, -304], type: 'n8n-nodes-base.httpRequest', typeVersion: 4.2,
      notes: '서버 검수 대기 저장과 동일한 AI_KNOW Ingestion Header Auth Credential을 사용합니다.',
      parameters: { url: baseUrl + '/internal/v1/images/storage', ...auth, options }, credentials, retryOnFail: false },
    code('aiknow-storage-check-done', '저장소 확인 후 수집', [648, -304], `const r = $input.first().json;
if (r.statusCode !== 200 || r.body?.ready !== true) throw new Error('Image storage is not ready. No paid generation was started.');
const expected = ${JSON.stringify(media)};
if (expected.publicBaseUrl && (r.body.publicBaseUrl !== expected.publicBaseUrl || r.body.mode !== expected.mode)) throw new Error('Server media settings differ from workflow environment. No paid generation was started.');
return [{ json: $('실행 설정').first().json, pairedItem: { item: 0 } }];`),
    code('aiknow-upload-prepare', '이미지 업로드 준비', [2496, 688], `${prepareImageUploads.toString()}\nreturn prepareImageUploads($input.all());`),
    condition('aiknow-upload-needed', '생성 이미지 업로드 여부', [2720, 688], '={{ $json.uploadRequired }}'),
    { id: 'aiknow-image-upload', name: '생성 이미지 서버 업로드', position: [2944, 592], type: 'n8n-nodes-base.httpRequest', typeVersion: 4.2,
      notes: '서버 검수 대기 저장과 동일한 AI_KNOW Ingestion Header Auth Credential을 사용합니다.',
      parameters: { method: 'POST', url: baseUrl + '/internal/v1/images', ...auth, sendBody: true,
        contentType: 'multipart-form-data', bodyParameters: { parameters: [{ parameterType: 'formBinaryData', name: 'file', inputDataFieldName: 'file' }] },
        options }, credentials, retryOnFail: false },
    code('aiknow-upload-done', '이미지 URL 반영', [3168, 688], `${applyUploadedImages.toString()}\nreturn applyUploadedImages($('이미지 업로드 준비').all(), $input.all(), ${JSON.stringify(media)});`),
  ];
}

export function imageUploadConnections() {
  const link = node => ({ node, type: 'main', index: 0 });
  const chain = (a, b) => [a, { main: [[link(b)]] }];
  return Object.fromEntries([
    chain('실행 설정', '이미지 생성 저장소 점검 여부'),
    ['이미지 생성 저장소 점검 여부', { main: [[link('이미지 저장소 연결 점검')], [link('TechCrunch AI RSS')]] }],
    chain('이미지 저장소 연결 점검', '저장소 확인 후 수집'), chain('저장소 확인 후 수집', 'TechCrunch AI RSS'),
    chain('초안 검증 완료', '이미지 업로드 준비'), chain('이미지 업로드 준비', '생성 이미지 업로드 여부'),
    ['생성 이미지 업로드 여부', { main: [[link('생성 이미지 서버 업로드')], [link('이미지 URL 반영')]] }],
    chain('생성 이미지 서버 업로드', '이미지 URL 반영'), chain('이미지 URL 반영', '서버 요청 변환'),
  ]);
}
