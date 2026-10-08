import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import * as fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { configureWorkflow } from './workflow-environment.mjs';
import { ingestionCredential, bindIngestionCredential, sameCredentialBindings, syncIngestionCredential } from './ingestion-credential.mjs';

const sourceDirectory = '/opt/aiknow/workflows';
const stateDirectory = '/home/node/.n8n/aiknow-deploy';

function sorted(value) {
  if (Array.isArray(value)) return value.map(sorted);
  if (value && typeof value === 'object') return Object.fromEntries(
    Object.keys(value).sort().map(key => [key, sorted(value[key])]));
  return value;
}

// Credentials, execution history, activation and editor pin data belong to the target instance.
export function definition(workflow) {
  return {
    id: workflow.id, name: workflow.name,
    nodes: workflow.nodes.map(({ credentials, ...node }) => node),
    connections: workflow.connections, settings: workflow.settings ?? {},
    nodeGroups: workflow.nodeGroups ?? [],
  };
}

export function fingerprint(workflow) {
  return createHash('sha256').update(JSON.stringify(sorted(definition(workflow)))).digest('hex');
}

export function validate(workflow) {
  if (!workflow || Array.isArray(workflow) || !/^[A-Za-z0-9_-]{1,64}$/.test(workflow.id ?? '')
      || typeof workflow.name !== 'string' || !workflow.name.trim()
      || !Array.isArray(workflow.nodes) || !workflow.nodes.length
      || !workflow.connections || typeof workflow.connections !== 'object' || Array.isArray(workflow.connections)) {
    throw new Error('Expected one workflow object with a stable id, name, nodes and connections.');
  }
  const ids = new Set();
  const names = new Set();
  for (const node of workflow.nodes) {
    if (!node.id || !node.name || !node.type || ids.has(node.id) || names.has(node.name)) {
      throw new Error('Every node must have a unique id/name and a type.');
    }
    ids.add(node.id); names.add(node.name);
  }
}

export function decision(source, current, record, replace = false) {
  if (record && record.workflowId !== source.id) throw new Error('Workflow id changed. Keep the original id when updating.');
  if (!current) return 'import';
  if (record?.sourceHash === fingerprint(source)) return 'unchanged';
  if (fingerprint(source) === fingerprint(current)) return 'adopt';
  if (!replace && (!record || fingerprint(current) !== record.liveHash)) {
    throw new Error('Existing n8n content differs from the deployment baseline. Export/review the UI changes first; use --replace only to intentionally replace them.');
  }
  return 'import';
}

export function importPayload(source, current) {
  const payload = definition(source);
  payload.nodes = payload.nodes.map(node => {
    const previous = current?.nodes.find(candidate => candidate.id === node.id && candidate.type === node.type);
    const credentialTypes = Object.keys(source.nodes.find(candidate => candidate.id === node.id).credentials ?? {});
    const credentials = Object.fromEntries(credentialTypes.filter(type => previous?.credentials?.[type])
      .map(type => [type, previous.credentials[type]]));
    // New media nodes may reuse the target instance's existing ingestion token,
    // but only for the exact API paths on that same backend.
    const suffix = { 'aiknow-image-upload': '/internal/v1/images', 'aiknow-storage-check': '/internal/v1/images/storage',
      'aiknow-generation-existing': '/internal/v1/generation/existing', 'aiknow-generation-claim': '/internal/v1/generation/claim',
      'aiknow-generation-save': '/internal/v1/generation/response',
      'aiknow-image-claim': '/internal/v1/generation/claim', 'aiknow-image-persist': '/internal/v1/images' }[node.id];
    const ingestion = current?.nodes.find(n => n.id === 'aiknow-server-import' && n.type === 'n8n-nodes-base.httpRequest');
    const importUrl = ingestion?.parameters?.url;
    if (!credentials.httpHeaderAuth && suffix && typeof importUrl === 'string'
      && importUrl.endsWith('/internal/v1/card-news/import')
      && node.type === 'n8n-nodes-base.httpRequest'
      && node.parameters.url === importUrl.slice(0, -'/internal/v1/card-news/import'.length) + suffix
      && ingestion.credentials?.httpHeaderAuth) {
      credentials.httpHeaderAuth = ingestion.credentials.httpHeaderAuth;
    }
    return Object.keys(credentials).length ? { ...node, credentials } : node;
  });
  return { ...payload, active: false, activeVersionId: null, pinData: {}, staticData: current?.staticData ?? null };
}

function runCli(args, { allowEmpty = false } = {}) {
  const result = spawnSync('n8n', args, {
    encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, timeout: 120000,
    env: { ...process.env, N8N_LOG_LEVEL: 'debug' },
  });
  const diagnostics = (result.stdout ?? '') + (result.stderr ?? '');
  // Do not print credential references, workflow code or CLI diagnostics containing user content.
  if (result.error || (result.status !== 0 && !(allowEmpty && diagnostics.includes('No workflows found with specified filters')))) {
    throw new Error('n8n CLI failed; check the n8n version, database and owner setup.');
  }
  return diagnostics;
}

function exportAll(tempDirectory, cli = runCli) {
  const output = path.join(tempDirectory, 'workflows.json');
  fs.rmSync(output, { force: true });
  const diagnostics = cli(['export:workflow', '--all', `--output=${output}`], { allowEmpty: true });
  // n8n 2.42 reports an empty instance as an export error instead of writing [].
  if (!fs.existsSync(output) && diagnostics.includes('No workflows found with specified filters')) return [];
  // Some CLI failure handlers exit with code zero, so require and validate the actual output.
  if (!fs.existsSync(output)) throw new Error('n8n export did not produce a file. Complete owner setup first.');
  const workflows = JSON.parse(fs.readFileSync(output, 'utf8'));
  if (!Array.isArray(workflows)) throw new Error('Unexpected n8n export format.');
  return workflows;
}

function saveJson(file, value) {
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
}

export function main(args = process.argv.slice(2), runtime = {}) {
  const env = runtime.env ?? process.env;
  const cli = runtime.cli ?? runCli;
  const sources = runtime.sourceDirectory ?? sourceDirectory;
  const state = runtime.stateDirectory ?? stateDirectory;
  const [action = 'check', filename = 'ai-know-news-draft.json', ...flags] = args;
  if (!['check', 'apply'].includes(action) || !/^[a-zA-Z0-9_-]+\.json$/.test(filename)
      || flags.some(flag => flag !== '--replace')) throw new Error('Usage: check|apply <filename.json> [--replace]');
  const source = configureWorkflow(JSON.parse(fs.readFileSync(path.join(sources, filename), 'utf8').replace(/^\uFEFF/, '')), env);
  validate(source);
  if (action === 'apply' && env.AIKNOW_ENV === 'prod' && !env.N8N_INGEST_TOKEN) {
    throw new Error('Use deploy.sh/deploy.ps1 apply with the running production server to provision N8N_INGEST_TOKEN.');
  }
  const credential = action === 'apply' && env.N8N_INGEST_TOKEN
    ? ingestionCredential(source.id, env.N8N_INGEST_TOKEN) : null;
  const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-workflow-'));
  try {
    const current = exportAll(tempDirectory, cli).find(workflow => workflow.id === source.id);
    const recordPath = path.join(state, `${filename}.state.json`);
    const record = fs.existsSync(recordPath) ? JSON.parse(fs.readFileSync(recordPath, 'utf8')) : null;
    const outcome = decision(source, current, record, flags.includes('--replace'));
    console.log(`${outcome.toUpperCase()}: ${filename} (workflow ${source.id})`);
    if (action === 'check') return;
    let payload = importPayload(outcome === 'import' ? source : current, current);
    if (credential) {
      payload = bindIngestionCredential(payload, env.AIKNOW_SERVER_BASE_URL, credential);
      syncIngestionCredential(credential, current, tempDirectory, cli);
      console.log('Ingestion credential synchronized from the server environment.');
    }
    const bindingChanged = credential && current && !sameCredentialBindings(payload, current);
    if (outcome === 'unchanged' && !bindingChanged) return;
    fs.mkdirSync(state, { recursive: true, mode: 0o700 });
    let deployed = current;
    if (outcome === 'import' || bindingChanged) {
      if (current) {
        const backupDirectory = path.join(state, 'backups');
        fs.mkdirSync(backupDirectory, { recursive: true, mode: 0o700 });
        const backup = path.join(backupDirectory, `${source.id}-${Date.now()}.json`);
        saveJson(backup, [current]);
        console.log(`Previous workflow backed up to ${backup}`);
      }
      const input = path.join(tempDirectory, 'import.json');
      saveJson(input, [payload]);
      const ownerProject = current?.shared?.find(entry => entry.role === 'workflow:owner')?.projectId;
      cli(['import:workflow', `--input=${input}`, '--activeState=false', ...(ownerProject ? [`--projectId=${ownerProject}`] : [])]);
      deployed = exportAll(tempDirectory, cli).find(workflow => workflow.id === source.id);
      if (!deployed || fingerprint(deployed) !== fingerprint(payload) || deployed.active || deployed.activeVersionId
        || (credential && !sameCredentialBindings(payload, deployed))) {
        throw new Error('Import verification failed. Deployment state was not advanced. Review the backup before retrying.');
      }
      if (JSON.stringify(sorted(deployed.staticData ?? null)) !== JSON.stringify(sorted(current?.staticData ?? null))) {
        throw new Error('Workflow history verification failed. Deployment state was not advanced.');
      }
      console.log('Imported as an unpublished draft. Check OpenAI credentials and publish in n8n when ready.');
    }
    // A binding-only update must not silently adopt unrelated UI edits as a new baseline.
    if (outcome === 'unchanged') return;
    const nextRecord = { workflowId: source.id, sourceHash: fingerprint(source), liveHash: fingerprint(deployed), appliedAt: new Date().toISOString() };
    const pending = `${recordPath}.tmp`;
    saveJson(pending, nextRecord);
    fs.renameSync(pending, recordPath);
    console.log('Deployment baseline saved. Container restarts do not import workflows.');
  } finally {
    fs.rmSync(tempDirectory, { recursive: true, force: true });
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try { main(); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
