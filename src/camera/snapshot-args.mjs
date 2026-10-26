import { parseArgs } from 'node:util';
import { createTokenStore } from './token-store.mjs';

export const USAGE = 'Usage: node src/camera/snapshot.mjs pair --url http://PHONE_IP:8765 --code 123456 | {status|shot|smoke} [--url http://PHONE_IP:8765] [--token T] | unpair | clean  [--dry-run]';

// The pairing code is only ever on argv, so it (and any 64-hex token) is redacted from the run log, including the argv line.
export const redaction = { shapes: [/(?<="--code",")\d{6}(?=")/g, /(?<=--code=)\d{6}/g, /\b[0-9a-f]{64}\b/g] };

export function parse(args) {
  const [command, ...rest] = args;
  let values;
  try { ({ values } = parseArgs({ args: rest, options: { url: { type: 'string' }, code: { type: 'string' }, token: { type: 'string' } }, strict: true, allowPositionals: false })); }
  catch { throw new Error(USAGE); }
  return { command, ...values };
}

// Token precedence: --token, then AGENT_EYE_TOKEN, then the token stored by `pair`.
// --url falls back to the URL the token was paired with.
export async function resolveTarget({ url, token }, { env = process.env, tokens = createTokenStore() } = {}) {
  const stored = token || env.AGENT_EYE_TOKEN ? undefined : await tokens.load();
  return { url: url ?? stored?.url, token: token || env.AGENT_EYE_TOKEN || stored?.token };
}
