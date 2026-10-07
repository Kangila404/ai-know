import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

export function components(files) {
  const pipeline = files.some(f => f.startsWith('.github/workflows/') || f.startsWith('scripts/'));
  const infra = pipeline || files.some(f => f.startsWith('infra/') && !f.endsWith('.md'));
  return {
    server: pipeline || files.some(f => f.startsWith('server/') && !f.endsWith('.md')),
    workflow: infra || files.some(f => f.startsWith('infra/n8n/') && !f.endsWith('.md')),
    infra,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const [base, head] = process.argv.slice(2);
  if (![base, head].every(s => /^[a-f0-9]{40}$/i.test(s ?? ''))) throw new Error('Pass full base/head commit SHAs');
  const files = /^0+$/.test(base)
    ? execFileSync('git', ['ls-tree', '-r', '--name-only', head], { encoding: 'utf8' }).trim().split('\n')
    : execFileSync('git', ['diff', '--name-only', base, head], { encoding: 'utf8' }).trim().split('\n');
  const result = components(files);
  for (const [key, value] of Object.entries(result)) {
    console.log(`${key}=${value}`);
    if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`);
  }
}
