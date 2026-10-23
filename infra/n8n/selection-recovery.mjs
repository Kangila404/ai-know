// Only recover a reservation's ORIGINAL input, never a later BLOCKED rerun.
export function selectionRecovery(runData, recordId) {
  for (const run of runData['모델 호출 준비'] ?? []) {
    const state = run.data?.main?.[0]?.[0]?.json;
    if (state?.task !== 'image_selection' || String(state.cacheClaim?.id) !== String(recordId)
        || !['CALL', 'REPLAY'].includes(state.cacheClaim.decision)) continue;
    if (!state.request || !state.draft || !Array.isArray(state.assets) || state.assets.length > 5)
      throw new Error('Original selection context is incomplete');
    return { id: Number(recordId), requestJson: JSON.stringify(state.request),
      contextJson: JSON.stringify({version: 1, draft: state.draft, assets: state.assets}) };
  }
  return null;
}
