// Embedded in the response validator. The server reserves a single text_retry
// slot per source URL, so restarting or clicking Execute cannot reset the budget.
export function modelFailure(state, reason) {
  const s = state;
  const originalId = s.cacheClaim?.id;
  if (!s.retryUsed && !s.retryOf && !s.config.dryRun && Number.isInteger(originalId)
      && ['draft', 'image_selection'].includes(s.task) && s.request?.input) {
    const request = JSON.parse(JSON.stringify(s.request));
    request.input.push({role:'user', content:JSON.stringify({
      validationError:String(reason).slice(0,600),
      instruction:'이전 요청은 실패했습니다. 같은 원문과 스키마로 한 번만 다시 작성하세요. 근거 없는 수치와 주장을 빼고, 원문 근거 ID와 한국어 형식을 확인하세요. 이미지 선택은 주어진 후보만 사용하고 적합한 것이 없으면 null로 반환하세요.'
    })});
    const chars = request.input.filter(item => item.role !== 'system').reduce((sum,item)=>sum+String(item.content).length,0);
    if (chars <= s.config.maxModelInputChars && s.counters.textCalls < 7) {
      s.retryUsed = true;
      s.retryOf = originalId;
      s.modelRetryRequest = request;
      s.modelRetryTask = s.task;
      s.phase = 'model_retry';
      delete s.status; delete s.reason;
      s.audit.push({event:'automatic_retry_scheduled',detail:{originalId,task:s.task,reason:String(reason).slice(0,600),maxRetries:1}});
      return;
    }
  }
  s.phase = 'stop'; s.status = 'fallback'; s.reason = String(reason);
  s.fallback = {mode:'server_daily_edition',priority:['APPROVED_NEWS','APPROVED_AI_THEORY'],requiresApproval:true};
  s.audit.push({event:'automatic_retry_exhausted',detail:{reason:String(reason).slice(0,600),retryOf:s.retryOf??null}});
}

export function dispatchModelRetry(state) {
  const s = state;
  if (s.phase !== 'model_retry') return false;
  if (!s.retryUsed || !Number.isInteger(s.retryOf) || !s.modelRetryRequest
      || !['draft','image_selection'].includes(s.modelRetryTask)) throw new Error('잘못된 자동 재시도 상태');
  s.request = s.modelRetryRequest; delete s.modelRetryRequest;
  s.task = s.modelRetryTask; delete s.modelRetryTask;
  s.counters.textCalls++;
  s.route = 1;
  return true;
}
