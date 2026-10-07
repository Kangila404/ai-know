import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aiknow-compose-'));
try {
  const example = fs.readFileSync('infra/.env.prod.example', 'utf8');
  const server = path.join(dir, 'server.env').replaceAll('\\', '/');
  fs.writeFileSync(server, fs.readFileSync('server/.env.prod.example', 'utf8'));
  const values = { MYSQL_PASSWORD:'ci-test-only', MYSQL_ROOT_PASSWORD:'ci-root-test-only',
    N8N_ENCRYPTION_KEY:'ci-encryption-test-only-at-least-32-characters', SERVER_ENV_FILE:server,
    TLS_CERT_PATH:dir.replaceAll('\\','/'), MEDIA_HOST_PATH:dir.replaceAll('\\','/'), SERVER_SECRETS_PATH:dir.replaceAll('\\','/') };
  let content=example;
  for(const [key,value]of Object.entries(values))content=content.replace(new RegExp('^'+key+'=.*$','m'),()=>key+'='+value);
  const file=path.join(dir,'infra.env');fs.writeFileSync(file,content);
  execFileSync('docker',['compose','--env-file',file,'-f','infra/docker-compose.prod.yml','config','--quiet'],{stdio:'inherit'});
  console.log('Production Compose references resolve with isolated test settings. No containers were started.');
} finally {
  const resolved=path.resolve(dir);
  if(path.dirname(resolved)!==path.resolve(os.tmpdir())||!path.basename(resolved).startsWith('aiknow-compose-'))throw new Error('Unexpected cleanup path');
  fs.rmSync(resolved,{recursive:true,force:true});
}
