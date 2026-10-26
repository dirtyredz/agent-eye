// Standard script wrapper - canonical copy; copy VERBATIM into other repos (never edit per repo; extend via options).
// Version: 1.1.0 (bump on any API change). One dependency-free file (Node built-ins only) on purpose.
//
//   import { runScript } from '<path>/script-run.mjs';
//   await runScript({ name: 'my-script', from: import.meta.url, kind: 'script', secrets: ['MY_TOKEN'] },
//     async log => { log.step('doing x'); const run = log.wrapRun(myRunner); await run('git', ['status']); });
//
// Options: name, from (caller import.meta.url), kind ('script' -> .tmp/logs, 'owner' -> .tmp/owner-runs),
//   logDir (overrides kind; relative to the root), secrets (env var names), argv,
//   redaction { envPattern, shapes } (extra RegExp(s) added to the defaults), env, root, echo, now.
// - Repo root: walks up from `from` to the nearest directory holding .git or package.json. Never uses
//   process.cwd(), so scripts behave the same from any directory.
// - Log: <root>/.tmp/logs/<name>-<UTC stamp>.log (or owner-runs). Records start/end, argv, steps, every
//   wrapped child command + exit code, and the final status.
// - Redaction: the LOG cleans every line it writes, whatever runner produced the text: values of env vars named in
//   `secrets`, of env vars matching the sensitive pattern, of log.addSecrets(...), and common token shapes become
//   [REDACTED]. A base runner passed to log.wrapRun need not redact for the log to stay clean (its return value and
//   errors still reach the caller unchanged; only the log and the printed error are cleaned).
// - log.argv is the original argv; log.args is argv without a single `--dry-run`, which sets log.dryRun.
// - Failing with a chosen code: throw an error carrying `exitCode` (default 1).
// - Always ends with one line `OK (exit 0) log: <path>` or `FAILED (exit N) log: <path>`, and sets process.exitCode.
import { appendFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const SCRIPT_RUN_VERSION = '1.1.0';
const SENSITIVE_ENV = /key|pass|token|secret|credential/i;
const TOKEN_SHAPES = [/gh[pousr]_[A-Za-z0-9]{20,}/g, /github_pat_\w{20,}/g, /Bearer\s+[\w.~+/-]+=*/gi, /AKIA[0-9A-Z]{16}/g, /sk-[A-Za-z0-9_-]{20,}/g, /eyJ[\w-]{10,}\.[\w-]{10,}\.[\w-]*/g];
const LOG_DIRS = { script: '.tmp/logs', owner: '.tmp/owner-runs' };
const globalShape = pattern => (pattern.global ? pattern : new RegExp(pattern.source, `${pattern.flags}g`));

export function findRoot(from) {
  let dir = dirname(fileURLToPath(from));
  for (;;) {
    if (existsSync(join(dir, '.git')) || existsSync(join(dir, 'package.json'))) return dir;
    const parent = dirname(dir);
    if (parent === dir) throw new Error(`No repo root (.git or package.json) above ${from}`);
    dir = parent;
  }
}

export function createScriptLog({ name, from, kind = 'script', logDir, secrets = [], redaction = {}, argv = process.argv.slice(2), env = process.env, root = findRoot(from), echo = console.log, now = () => new Date() }) {
  if (!logDir && !Object.hasOwn(LOG_DIRS, kind)) throw new Error(`Unknown script kind: ${kind}`);
  const dir = join(root, logDir ?? LOG_DIRS[kind]);
  mkdirSync(dir, { recursive: true });
  const path = join(dir, `${name}-${now().toISOString().replace(/[:.]/g, '-')}.log`);
  const envPattern = new RegExp((redaction.envPattern ?? SENSITIVE_ENV).source, 'i');
  const shapes = [...TOKEN_SHAPES, ...[redaction.shapes ?? []].flat()].map(globalShape);
  const known = new Set();
  const addSecrets = (...values) => { for (const value of values.flat()) if (typeof value === 'string' && value.length >= 4) known.add(value); };
  addSecrets(secrets.map(variable => env[variable]));
  addSecrets(Object.entries(env).filter(([key]) => SENSITIVE_ENV.test(key) || envPattern.test(key)).map(([, value]) => value));
  const clean = input => {
    let text = String(input);
    for (const secret of [...known].sort((a, b) => b.length - a.length)) text = text.split(secret).join('[REDACTED]');
    for (const shape of shapes) text = text.replace(shape, '[REDACTED]');
    return text;
  };
  const write = line => appendFileSync(path, `${new Date().toISOString()} ${clean(line)}\n`);
  const dryIndex = argv.indexOf('--dry-run');
  const args = dryIndex < 0 ? argv : [...argv.slice(0, dryIndex), ...argv.slice(dryIndex + 1)];

  // Adapt an existing run(file, args, options) so it defaults cwd to the root and every call is logged.
  const wrapRun = base => async (file, callArgs = [], options = {}) => {
    addSecrets(options.secrets ?? []);
    write(`exec: ${file} ${JSON.stringify(callArgs)} cwd=${options.cwd ?? root}`);
    try { const result = await base(file, callArgs, { ...options, cwd: options.cwd ?? root }); write(`exit: ${result?.code ?? 0} ${file}`); return result; }
    catch (error) { write(`failed: ${file}: ${String(error.message).split('\n')[0]}`); throw error; }
  };
  return { path, root, name, addSecrets, step: message => write(`step: ${message}`), wrapRun, write, clean, argv, args, dryRun: dryIndex >= 0, echo };
}

export async function runScript(options, fn) {
  const log = createScriptLog(options);
  log.write(`start ${log.name} cwd=${process.cwd()} root=${log.root}`);
  log.write(`argv: ${JSON.stringify(log.argv)}`);
  log.echo(`log: ${log.path}`);
  let code = 0;
  try { await fn(log); if (process.exitCode) code = Number(process.exitCode); }
  catch (error) {
    code = Number.isInteger(error?.exitCode) && error.exitCode > 0 ? error.exitCode : 1;
    log.write(`error: ${String(error?.stack ?? error?.message ?? error)}`);
    console.error(log.clean(error?.message ?? error));
  }
  log.write(`end ${log.name} exit=${code}`);
  process.exitCode = code;
  log.echo(`${code === 0 ? 'OK' : 'FAILED'} (exit ${code}) log: ${log.path}`);
  return code;
}
