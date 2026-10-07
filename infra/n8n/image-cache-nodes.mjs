import { applyUploadedImages } from './image-upload-nodes.mjs';

export function imageCacheNodes(baseUrl, credentials, media = {}) {
  const code = (id, name, jsCode) => ({ id, name, type: 'n8n-nodes-base.code', typeVersion: 2,
    position: [1200, 1100], parameters: { jsCode } });
  const options = { timeout: 60000, redirect: { redirect: { followRedirects: false } },
    response: { response: { fullResponse: true, responseFormat: 'json' } } };
  const http = (id, name, path, body) => ({ id, name, type: 'n8n-nodes-base.httpRequest', typeVersion: 4.2,
    position: [1400, 1100], credentials, retryOnFail: false, parameters: { method: 'POST', url: baseUrl + path,
      authentication: 'genericCredentialType', genericAuthType: 'httpHeaderAuth', sendBody: true, ...body, options } });
  const attachReceipt = `${applyUploadedImages.toString()}
function attach(s,receipt,index) {
  const asset=s.assets[index];
  const plan=[{json:{draft:{slides:[{image:{origin:'generated',url:null,binaryProperty:'image_'+asset.key}}]},uploadRequired:true,binaryProperty:'image_'+asset.key}}];
  const image=applyUploadedImages(plan,[{json:{statusCode:201,body:receipt}}],${JSON.stringify(media)})[0].json.slides[0].image;
  asset.image={...image,generationPrompt:asset.generationPrompt,attribution:'AI 생성 이미지',verifiedAt:new Date().toISOString()};
}`;
  return [
    http('aiknow-image-claim', '이미지 호출 예약 또는 캐시 조회', '/internal/v1/generation/claim', {
      specifyBody: 'json', jsonBody: "={{ JSON.stringify({sourceUrl:$json.current.sourceUrl,task:'image_generation_'+$json.assets[$json.assetIndex].key,requestJson:JSON.stringify($json.request)}) }}" }),
    code('aiknow-image-prepare', '이미지 호출 준비', `const r=$input.first().json;
if(r.statusCode!==200||!['CALL','REPLAY','BLOCKED'].includes(r.body?.decision))throw new Error('이미지 예약 실패. 유료 호출하지 않습니다.');
const before=$('작업 결정').itemMatching(0),s=JSON.parse(JSON.stringify(before.json));
s.imageClaim=r.body;s.imageCacheRoute={CALL:0,REPLAY:1,BLOCKED:2}[r.body.decision];
s.counters.paidImageCalls=(s.counters.paidImageCalls||0)+(r.body.decision==='CALL'?1:0);
if(r.body.decision==='BLOCKED'){s.phase='stop';s.status='blocked';s.reason=r.body.reason;}
s.audit.push({event:'image_cache',detail:{decision:r.body.decision,id:r.body.id}});
return [{json:s,...(before.binary?{binary:before.binary}:{}),pairedItem:{item:0}}];`),
    { id:'aiknow-image-cache-route',name:'이미지 캐시 처리 경로',type:'n8n-nodes-base.switch',typeVersion:3.2,position:[1600,1100],
      parameters:{mode:'expression',numberOutputs:3,output:'={{ $json.imageCacheRoute }}'} },
    code('aiknow-image-replay', '저장 이미지 재사용', `${attachReceipt}
const item=$input.first(),s=JSON.parse(JSON.stringify(item.json));
if(s.imageClaim?.decision!=='REPLAY')throw new Error('이미지 캐시가 없습니다.');
attach(s,JSON.parse(s.imageClaim.responseJson),s.assetIndex);
s.assetIndex++;s.phase='assets';s.counters.imageCacheHits=(s.counters.imageCacheHits||0)+1;
return [{json:s,...(item.binary?{binary:item.binary}:{}),pairedItem:{item:0}}];`),
    { id:'aiknow-image-persist-needed',name:'생성 이미지 즉시 저장 여부',type:'n8n-nodes-base.if',typeVersion:2.2,position:[1800,1100],
      parameters:{conditions:{options:{caseSensitive:true,leftValue:'',typeValidation:'strict',version:2},
        conditions:[{id:'image-ready',leftValue:"={{ $json.phase === 'assets' }}",rightValue:true,
          operator:{type:'boolean',operation:'true',singleValue:true}}],combinator:'and'},options:{}} },
    http('aiknow-image-persist', '생성 이미지 즉시 저장', '/internal/v1/images', {
      contentType:'multipart-form-data',bodyParameters:{parameters:[
        {parameterType:'formBinaryData',name:'file',inputDataFieldName:'={{ $json.assets[$json.assetIndex-1].image.binaryProperty }}'},
        {name:'generationRecordId',value:'={{ String($json.imageClaim.id) }}'}]} }),
    code('aiknow-image-persisted', '이미지 캐시 저장 확인', `${attachReceipt}
const r=$input.first().json;
if(r.statusCode!==201)throw new Error('이미지 저장 실패. 원래 실행의 Binary로 복구하고 이미지를 재생성하지 마세요.');
const before=$('생성 이미지 확보').itemMatching(0),s=JSON.parse(JSON.stringify(before.json));
attach(s,r.body,s.assetIndex-1);
return [{json:s,...(before.binary?{binary:before.binary}:{}),pairedItem:{item:0}}];`),
  ];
}

export function imageCacheConnections() {
  const edge = node => ({node,type:'main',index:0});
  return {
    '이미지 호출 예약 또는 캐시 조회':{main:[[edge('이미지 호출 준비')]]},
    '이미지 호출 준비':{main:[[edge('이미지 캐시 처리 경로')]]},
    '이미지 캐시 처리 경로':{main:[[edge('개념 이미지 생성 API')],[edge('저장 이미지 재사용')],[edge('점검 또는 정상 종료')]]},
    '저장 이미지 재사용':{main:[[edge('작업 결정')]]},
    '생성 이미지 확보':{main:[[edge('생성 이미지 즉시 저장 여부')]]},
    '생성 이미지 즉시 저장 여부':{main:[[edge('생성 이미지 즉시 저장')],[edge('점검 또는 정상 종료')]]},
    '생성 이미지 즉시 저장':{main:[[edge('이미지 캐시 저장 확인')]]},
    '이미지 캐시 저장 확인':{main:[[edge('작업 결정')]]},
  };
}
