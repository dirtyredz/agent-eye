// Standard script wrapper - canonical copy; keep in sync across repos.
// Version: 1.0.0. Dependency-free (Node built-ins only) so it can be copied verbatim.
//
//   import { runScript } from '<path>/script-run.mjs';
//   await runScript({ name: 'my-script', from: import.meta.url, ownerRun: false, secrets: ['MY_TOKEN'] },
//     async log => { log.step('doing x'); await log.exec('git', ['status']); });
//
// - Repo root: walks up from `from` (the caller's import.meta.url) to the nearest directory holding .git or
//   package.json. Never uses process.cwd(), so scripts behave the same from any directory.
// - Log: <root>/.tmp/logs/<name>-<UTC stamp>.log, or <root>/.tmp/owner-runs/ when ownerRun is true.
// - Records start/end, argv, steps, every child command + exit code, and the final status.
// - Redaction: values of env vars named in `secrets`, of sensitive-looking env vars (key/pass/token/secret/
//   credential), of log.addSecrets(...), and common token shapes are replaced with [REDACTED] everywhere.
// - Always ends with one line `OK (exit 0) log: <path>` or `FAILED (exit N) log: <path>` and sets process.exitCode.
import { spawn } from 'node:child_process';
import { appendFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const SCRIPT_RUN_VERSION = '1.0.0';
const SENSITIVE_ENV = /key|pass|token|secret|credential/i;
const TOKEN_SHAPES = [/gh[pousr]_[A-Za-z0-9]{20,}/g, /github_pat_\w{20,}/g, /Bearer\s+[\w.~+/-]+=*/gi, /AKIA[0-9A-Z]{16}/g, /sk-[A-Za-z0-9_-]{20,}/g, /eyJ[\w-]{10,}\.[\w-]{10,}\.[\w-]*/g];

export function findRoot(from) {
  let dir = dirname(fileURLToPath(from));
  for (;;) {
    if (existsSync(join(dir, '.git')) || existsSync(join(dir, 'package.json'))) return dir;
    const parent = dirname(dir);
    if (parent === dir) throw new Error(`No repo root (.git or package.json) above ${from}`);
    dir = parent;
  }
}

export function createScriptLog({ name, from, ownerRun = false, secrets = [], argv = process.argv.slice(2), env = process.env, root = findRoot(from), echo = console.log, now = () => new Date() }) {
  const dir = join(root, '.tmp', ownerRun ? 'owner-runs' : 'logs');
  mkdirSync(dir, { recursive: true });
  const path = join(dir, `${name}-${now().toISOString().replace(/[:.]/g, '-')}.log`);
  const known = new Set();
  const addSecrets = (...values) => { for (const value of values.flat()) if (typeof value === 'string' && value.length >= 4) known.add(value); };
  addSecrets(secrets.map(variable => env[variable]));
  addSecrets(Object.entries(env).filter(([key]) => SENSITIVE_ENV.test(key)).map(([, value]) => value));
  const clean = input => {
    let text = String(input);
    for (const secret of [...known].sort((a, b) => b.length - a.length)) text = text.split(secret).join('[REDACTED]');
    for (const shape of TOKEN_SHAPES) text = text.replace(shape, '[REDACTED]');
    return text;
  };
  const write = line => appendFileSync(path, `${new Date().toISOString()} ${clean(line)}\n`);

  // Built-in child runner: no shell, explicit cwd (default root), timeout, bounded output, exit code logged.
  function exec(file, args = [], { cwd = root, env: extraEnv = {}, secrets: more = [], allowed = [0], timeout = 120000, maxBytes = 8 * 1024 * 1024 } = {}) {
    addSecrets(more);
    write(`exec: ${file} ${JSON.stringify(args)} cwd=${cwd}`);
    return new Promise((resolve, reject) => {
      const child = spawn(file, args, { cwd, env: { ...env, ...extraEnv }, windowsHide: true, shell: false });
      let stdout = '', stderr = '', bytes = 0, failure;
      const stop = reason => { failure = reason; child.kill(); };
      const timer = setTimeout(() => stop('Subprocess timeout'), timeout);
      const collect = key => data => {
        bytes += data.length;
        if (bytes > maxBytes) { stop('Subprocess output limit'); return; }
        if (key === 'stdout') stdout += data; else stderr += data;
      };
      child.stdout.on('data', collect('stdout')); child.stderr.on('data', collect('stderr'));
      child.on('error', error => { clearTimeout(timer); write(`failed: ${file}: ${error.message}`); reject(new Error(clean(error.message))); });
      child.on('close', code => {
        clearTimeout(timer);
        write(`exit: ${code} ${file}`);
        if (failure || !allowed.includes(code)) reject(new Error(clean(`${failure ?? `Subprocess failed (${code}): ${file}`}\n${stdout}\n${stderr}`)));
        else resolve({ code, stdout: clean(stdout), stderr: clean(stderr) });
      });
    });
  }
  // Adapt an existing run(file, args, options) so it defaults cwd to the root and is logged.
  const wrapRun = base => async (file, args = [], options = {}) => {
    addSecrets(options.secrets ?? []);
    write(`exec: ${file} ${JSON.stringify(args)} cwd=${options.cwd ?? root}`);
    try { const result = await base(file, args, { ...options, cwd: options.cwd ?? root }); write(`exit: ${result.code} ${file}`); return result; }
    catch (error) { write(`failed: ${file}: ${String(error.message).split('\n')[0]}`); throw error; }
  };
  return { path, root, name, addSecrets, step: message => write(`step: ${message}`), exec, wrapRun, write, clean, argv, echo };
}

export async function runScript(options, fn) {
  const log = createScriptLog(options);
  log.write(`start ${log.name} cwd=${process.cwd()} root=${log.root}`);
  log.write(`argv: ${JSON.stringify(log.argv)}`);
  log.echo(`log: ${log.path}`);
  let code = 0;
  try { await fn(log); if (process.exitCode) code = Number(process.exitCode); }
  catch (error) {
    code = 1;
    log.write(`error: ${String(error.stack ?? error.message)}`);
    console.error(log.clean(error.message));
  }
  log.write(`end ${log.name} exit=${code}`);
  process.exitCode = code;
  log.echo(`${code === 0 ? 'OK' : 'FAILED'} (exit ${code}) log: ${log.path}`);
  return code;
}
