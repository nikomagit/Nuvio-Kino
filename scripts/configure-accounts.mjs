// Only the public anonymous client key is persisted. Never accept an administrative key.
import { mkdir, writeFile, rename } from 'node:fs/promises';
const discoveryUrl = 'https://api.nuvio.tv/.well-known/nuvio';
const response = await fetch(discoveryUrl, { signal: AbortSignal.timeout(20000) });
if (!response.ok) throw new Error(`Account discovery HTTP ${response.status}`);
const document = await response.json();
if (document.version !== 1 || document.service !== 'nuvio' || document.backend_url !== 'https://api.nuvio.tv') {
  throw new Error('Unexpected official account discovery document');
}
if (!document.capabilities?.email_password_auth || !document.capabilities?.tv_login) {
  throw new Error('Account server does not advertise both login methods');
}
const key = document.publishable_key;
if (typeof key !== 'string' || /[\r\n\\]/.test(key)) throw new Error('Invalid public client key');
if (!key.startsWith('sb_publishable_')) {
  const claims = JSON.parse(Buffer.from(key.split('.')[1] ?? '', 'base64url').toString('utf8'));
  if (claims.role !== 'anon' || claims.iss !== 'supabase' || !Number.isFinite(claims.exp) || claims.exp * 1000 < Date.now()) {
    throw new Error('Only an unexpired anonymous public client key may be included');
  }
}
const settings = await fetch(`${document.backend_url}/auth/v1/settings`, {
  headers: { apikey: key }, signal: AbortSignal.timeout(20000),
});
if (!settings.ok) throw new Error(`Public account configuration rejected: HTTP ${settings.status}`);
const dir = new URL('../config/', import.meta.url);
await mkdir(dir, { recursive: true });
const output = new URL('official-accounts.properties', dir);
const temporary = new URL('official-accounts.properties.tmp', dir);
await writeFile(temporary, [
  '# Public client configuration, not an administrative credential.',
  `# Source: ${discoveryUrl}`,
  '# Refresh with: node scripts/configure-accounts.mjs',
  `NUVIO_SUPABASE_URL=${document.backend_url}`,
  `NUVIO_SUPABASE_ANON_KEY=${key}`,
  'NUVIO_SUPABASE_FALLBACK_URL=',
  '',
].join('\n'));
await rename(temporary, output);
console.log('Official public account configuration saved; auth settings HTTP 200.');
