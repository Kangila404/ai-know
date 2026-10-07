import { ingestionNodes } from './ingestion-nodes.mjs';
import { imageUploadNodes } from './image-upload-nodes.mjs';
import { imageCacheNodes } from './image-cache-nodes.mjs';

// Only non-secret runtime settings are embedded. Credentials stay in n8n.
export function readWorkflowEnvironment(env) {
  if (!env.AIKNOW_ENV) return null;
  if (!['local', 'prod'].includes(env.AIKNOW_ENV)) throw new Error('AIKNOW_ENV must be local or prod');
  const url = (key, httpsOnly = false) => {
    const value = env[key]?.replace(/\/+$/, '');
    let parsed;
    try { parsed = new URL(value); } catch { throw new Error(`Set a valid ${key}`); }
    if (!(httpsOnly ? parsed.protocol === 'https:' : ['http:', 'https:'].includes(parsed.protocol))
      || parsed.username || parsed.password || parsed.search || parsed.hash)
      throw new Error(`Invalid ${key}${httpsOnly ? ': production media requires HTTPS' : ''}`);
    return value;
  };
  const bool = key => {
    if (!['true', 'false'].includes(env[key])) throw new Error(`${key} must be true or false`);
    return env[key] === 'true';
  };
  if (!/^[0-3]$/.test(env.AIKNOW_MAX_IMAGE_GENERATIONS ?? ''))
    throw new Error('AIKNOW_MAX_IMAGE_GENERATIONS must be 0-3');
  return {
    serverBaseUrl: url('AIKNOW_SERVER_BASE_URL'),
    media: { mode: env.AIKNOW_ENV === 'local' ? 'local' : 'mounted',
      publicBaseUrl: url('AIKNOW_MEDIA_PUBLIC_BASE_URL', env.AIKNOW_ENV === 'prod') },
    dryRun: bool('AIKNOW_DRY_RUN'), allowImageGeneration: bool('AIKNOW_ALLOW_IMAGE_GENERATION'),
    maxImageGenerations: Number(env.AIKNOW_MAX_IMAGE_GENERATIONS),
  };
}

export function configureWorkflow(source, env) {
  const settings = readWorkflowEnvironment(env);
  if (!settings) return source;
  const wf = structuredClone(source);
  const config = wf.nodes.find(n => n.name === '실행 설정');
  if (!config) throw new Error('Missing execution settings node');
  for (const field of ['dryRun', 'allowImageGeneration', 'maxImageGenerations']) {
    const pattern = new RegExp(`(\\b${field}:\\s*)(?:true|false|\\d+)(?=,)`);
    if (!pattern.test(config.parameters.jsCode)) throw new Error(`Cannot configure ${field}`);
    config.parameters.jsCode = config.parameters.jsCode.replace(pattern, `$1${settings[field]}`);
  }
  const paths = {
    'aiknow-server-import': '/internal/v1/card-news/import',
    'aiknow-image-upload': '/internal/v1/images', 'aiknow-storage-check': '/internal/v1/images/storage',
    'aiknow-generation-existing': '/internal/v1/generation/existing',
    'aiknow-generation-claim': '/internal/v1/generation/claim',
    'aiknow-generation-save': '/internal/v1/generation/response',
    'aiknow-image-claim': '/internal/v1/generation/claim', 'aiknow-image-persist': '/internal/v1/images',
  };
  for (const node of wf.nodes) if (paths[node.id]) node.parameters.url = settings.serverBaseUrl + paths[node.id];
  const generated = [...ingestionNodes(settings.serverBaseUrl + paths['aiknow-server-import'], settings.media),
    ...imageUploadNodes(settings.serverBaseUrl, {}, settings.media), ...imageCacheNodes(settings.serverBaseUrl, {}, settings.media)];
  for (const id of ['aiknow-import-transform', 'aiknow-upload-done', 'aiknow-storage-check-done', 'aiknow-image-replay', 'aiknow-image-persisted']) {
    const node = wf.nodes.find(n => n.id === id);
    if (!node) throw new Error(`Missing environment-aware node ${id}`);
    node.parameters.jsCode = generated.find(n => n.id === id).parameters.jsCode;
  }
  return wf;
}
