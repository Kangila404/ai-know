import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const paths = {
  'aiknow-server-import': '/internal/v1/card-news/import',
  'aiknow-image-upload': '/internal/v1/images',
  'aiknow-storage-check': '/internal/v1/images/storage',
  'aiknow-generation-existing': '/internal/v1/generation/existing',
  'aiknow-generation-claim': '/internal/v1/generation/claim',
  'aiknow-generation-save': '/internal/v1/generation/response',
  'aiknow-image-claim': '/internal/v1/generation/claim',
  'aiknow-image-persist': '/internal/v1/images',
};

export function ingestionCredential(workflowId, token) {
  if (typeof token !== 'string' || token.length < 32 || /\s/.test(token)) {
    throw new Error('N8N_INGEST_TOKEN must contain at least 32 non-whitespace characters.');
  }
  return {
    id: `aiknow-ingest-${createHash('sha256').update(workflowId).digest('hex').slice(0, 16)}`,
    name: 'AI_KNOW Ingestion (env managed)', type: 'httpHeaderAuth',
    data: { name: 'Authorization', value: `Bearer ${token}` },
  };
}

// Bind only known internal nodes at the configured backend. Never attach the token
// to RSS/OpenAI/Commons or follow redirects to an untrusted host.
export function bindIngestionCredential(workflow, baseUrl, credential) {
  const base = baseUrl?.replace(/\/+$/, '');
  let url;
  try { url = new URL(base); } catch { throw new Error('Set AIKNOW_SERVER_BASE_URL for credential provisioning.'); }
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('Invalid ingestion backend URL.');
  }
  const result = structuredClone(workflow);
  let found = false;
  for (const node of result.nodes) {
    if (!paths[node.id]) continue;
    if (node.type !== 'n8n-nodes-base.httpRequest' || node.parameters?.url !== base + paths[node.id]
      || node.parameters.authentication !== 'genericCredentialType'
      || node.parameters.genericAuthType !== 'httpHeaderAuth'
      || node.parameters.options?.redirect?.redirect?.followRedirects !== false) {
      throw new Error('Internal API node settings differ from the configured backend. Review the workflow before provisioning credentials.');
    }
    if (node.id === 'aiknow-server-import') found = true;
    node.credentials = { ...node.credentials, httpHeaderAuth: { id: credential.id, name: credential.name } };
  }
  if (!found) throw new Error('Missing internal ingestion node.');
  return result;
}

export function sameCredentialBindings(left, right) {
  return left.nodes.every(node => !paths[node.id]
    || node.credentials?.httpHeaderAuth?.id === right.nodes.find(n => n.id === node.id)?.credentials?.httpHeaderAuth?.id);
}

export function syncIngestionCredential(credential, current, tempDirectory, cli) {
  const input = path.join(tempDirectory, 'ingestion-credential.json');
  const output = path.join(tempDirectory, 'ingestion-credential-check.json');
  const ownerProject = current?.shared?.find(entry => entry.role === 'workflow:owner')?.projectId;
  try {
    fs.writeFileSync(input, JSON.stringify([credential]), { mode: 0o600 });
    cli(['import:credentials', `--input=${input}`, ...(ownerProject ? [`--projectId=${ownerProject}`] : [])]);
    // Verify the persisted value because some n8n CLI errors exit with status zero.
    cli(['export:credentials', `--id=${credential.id}`, '--decrypted', `--output=${output}`]);
    const [saved] = JSON.parse(fs.readFileSync(output, 'utf8'));
    if (saved?.id !== credential.id || saved.type !== credential.type
      || saved.data?.name !== 'Authorization' || saved.data.value !== credential.data.value) {
      throw new Error('Credential verification failed.');
    }
  } catch {
    // Never propagate CLI output, parse errors or file contents containing the token.
    throw new Error('Ingestion credential synchronization failed. Check n8n owner setup and encryption key.');
  } finally {
    fs.rmSync(input, { force: true });
    fs.rmSync(output, { force: true });
  }
}
