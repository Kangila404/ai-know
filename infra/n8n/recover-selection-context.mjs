// Run via recover-selection.sh, with n8n stopped. No paid API calls or workflow execution.
import fs from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {createRequire} from 'node:module';
import {selectionRecovery} from './selection-recovery.mjs';
const {parse} = createRequire(import.meta.url)('/usr/local/lib/node_modules/n8n/node_modules/flatted');
const id = process.argv[2];
if (!/^[1-9][0-9]*$/.test(id ?? '') || !Number.isSafeInteger(Number(id))) throw new Error('Pass the generation record ID');
const base = new URL(process.env.AIKNOW_SERVER_BASE_URL);
if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash)
  throw new Error('Invalid configured backend URL');
const token = fs.readFileSync(0, 'utf8').trim();
if (token.length < 32 || /\s/.test(token)) throw new Error('Missing ingestion token on stdin');
const workflow = JSON.parse(fs.readFileSync(new URL('./workflows/ai-know-news-draft.json', import.meta.url), 'utf8'));
const db = new DatabaseSync('/home/node/.n8n/database.sqlite', {readOnly: true});
let found;
try {
  // Iterate oldest first: the original CALL predates any changed-input reruns.
  const rows = db.prepare('SELECT e.id,d.data FROM execution_entity e JOIN execution_data d ON d.executionId=e.id WHERE e.workflowId=? ORDER BY e.id ASC');
  for (const row of rows.iterate(workflow.id)) {
    const recovery = selectionRecovery(parse(row.data).resultData?.runData ?? {}, id);
    if (recovery) { found = {executionId: row.id, recovery}; break; }
  }
} finally { db.close(); }
if (!found) throw new Error('Original CALL/REPLAY execution is not retained. No data changed; do not delete the generation record.');
async function post(path, body) {
  const r = await fetch(base.href.replace(/\/$/, '') + path, {method:'POST', redirect:'error',
    headers:{'Content-Type':'application/json', Authorization:'Bearer '+token}, body:JSON.stringify(body), signal:AbortSignal.timeout(30000)});
  if (!r.ok) throw new Error('Context recovery API returned HTTP '+r.status+'; no paid call was made');
  return r.json();
}
const result = await post('/internal/v1/generation/selection-context/recover', found.recovery);
if (result.saved !== true) throw new Error('Context recovery was not confirmed');
console.log(JSON.stringify({generationRecordId:Number(id),originalExecutionId:found.executionId,contextRecovered:true,
  next:'Run the updated workflow. An absent original model response remains blocked; it is never regenerated automatically.'}));
