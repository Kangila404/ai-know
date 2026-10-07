export function generationNodes(baseUrl, credentials) {
  const code = (id, name, position, jsCode) => ({ id, name, position, type: 'n8n-nodes-base.code', typeVersion: 2, parameters: { jsCode } });
  const http = (id, name, position, path, jsonBody) => ({ id, name, position, type: 'n8n-nodes-base.httpRequest', typeVersion: 4.2,
    credentials, retryOnFail: false, parameters: { method: 'POST', url: baseUrl + path,
      authentication: 'genericCredentialType', genericAuthType: 'httpHeaderAuth', sendBody: true, specifyBody: 'json', jsonBody,
      options: { timeout: 30000, redirect: { redirect: { followRedirects: false } },
        response: { response: { fullResponse: true, responseFormat: 'json' } } } } });
  return [
    http('aiknow-generation-existing', 'DB 기존 기사 조회', [400, 272], '/internal/v1/generation/existing',
      '={{ JSON.stringify({sourceUrls: $json.candidates.map(c => c.sourceUrl)}) }}'),
    code('aiknow-generation-filter', '저장된 기사 제외', [624, 272], `const r=$input.first().json;
if(r.statusCode!==200||!Array.isArray(r.body)) throw new Error('기존 기사 조회 실패. 유료 API를 호출하지 않습니다.');
const s=JSON.parse(JSON.stringify($('최근 기사와 중복 검사').first().json));
const existing=new Set(r.body.map(x=>x.sourceUrl));
const unprocessed=s.candidates.filter(c=>!existing.has(c.sourceUrl));
s.audit.push({event:'candidate_selection',detail:{scanned:s.candidates.length,unprocessed:unprocessed.length,processingLimit:s.config.maxCandidates}});
s.candidates=unprocessed.slice(0,s.config.maxCandidates);
s.audit.push({event:'existing_submissions',detail:r.body});
return [{json:s,pairedItem:{item:0}}];`),
    http('aiknow-generation-claim', '모델 호출 예약 또는 캐시 조회', [1000, 736], '/internal/v1/generation/claim',
      '={{ JSON.stringify({sourceUrl:$json.current.sourceUrl,task:$json.task,requestJson:JSON.stringify($json.request)}) }}'),
    code('aiknow-generation-prepare', '모델 호출 준비', [1224, 736], `const r=$input.first().json;
if(r.statusCode!==200||!['CALL','REPLAY','BLOCKED'].includes(r.body?.decision)) throw new Error('모델 호출 예약 실패. 유료 재시도하지 않습니다.');
const before=$('작업 결정').itemMatching(0),s=JSON.parse(JSON.stringify(before.json));
s.cacheClaim=r.body;s.cacheRoute={CALL:0,REPLAY:1,BLOCKED:2}[r.body.decision];
s.counters.paidTextCalls=(s.counters.paidTextCalls||0)+(r.body.decision==='CALL'?1:0);
s.counters.textCacheHits=(s.counters.textCacheHits||0)+(r.body.decision==='REPLAY'?1:0);
if(r.body.decision==='BLOCKED'){s.phase='stop';s.status='blocked';s.reason=r.body.reason;}
s.audit.push({event:'generation_cache',detail:{decision:r.body.decision,id:r.body.id}});
return [{json:s,...(before.binary?{binary:before.binary}:{}),pairedItem:{item:0}}];`),
    { id: 'aiknow-generation-route', name: '모델 캐시 처리 경로', type: 'n8n-nodes-base.switch', typeVersion: 3.2, position: [1448, 736],
      parameters: { mode: 'expression', numberOutputs: 3, output: '={{ $json.cacheRoute }}' } },
    code('aiknow-generation-replay', '저장된 모델 응답 재사용', [1672, 848], `const s=$input.first().json;
if(s.cacheClaim?.decision!=='REPLAY') throw new Error('재사용 가능한 응답이 없습니다.');
return [{json:JSON.parse(s.cacheClaim.responseJson),pairedItem:{item:0}}];`),
    http('aiknow-generation-save', '모델 원본 응답 보관', [1672, 624], '/internal/v1/generation/response',
      "={{ JSON.stringify({id:$('모델 호출 준비').item.json.cacheClaim.id,responseJson:JSON.stringify($json)}) }}"),
    code('aiknow-generation-saved', '보관 후 응답 검증', [1896, 624], `const r=$input.first().json;
if(r.statusCode!==200||r.body?.saved!==true) throw new Error('모델 응답 보관 실패. 실행 이력에서 복구하세요. 유료 재호출은 차단됩니다.');
return [{json:$('텍스트 생성 API').item.json,pairedItem:{item:0}}];`),
  ];
}

export function generationConnections() {
  const e = node => ({ node, type: 'main', index: 0 });
  const chain = (a,b) => [a,{ main:[[e(b)]] }];
  return Object.fromEntries([
    chain('최근 기사와 중복 검사','DB 기존 기사 조회'), chain('DB 기존 기사 조회','저장된 기사 제외'), chain('저장된 기사 제외','작업 결정'),
    chain('모델 호출 예약 또는 캐시 조회','모델 호출 준비'), chain('모델 호출 준비','모델 캐시 처리 경로'),
    ['모델 캐시 처리 경로',{main:[[e('텍스트 생성 API')],[e('저장된 모델 응답 재사용')],[e('점검 또는 정상 종료')]]}],
    chain('텍스트 생성 API','모델 원본 응답 보관'), chain('모델 원본 응답 보관','보관 후 응답 검증'),
    chain('보관 후 응답 검증','텍스트 응답 검증'), chain('저장된 모델 응답 재사용','텍스트 응답 검증'),
  ]);
}
