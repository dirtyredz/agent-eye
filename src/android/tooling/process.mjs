import { spawn } from 'node:child_process';

const cleanupHooks = new Map();
let listening = false;
async function terminate() {
  // Stop and reap children before removing their key files, then release locks.
  for (const phase of [0, 1, 2]) await Promise.allSettled([...cleanupHooks].filter(([, entry]) => entry.phase === phase).map(([cleanup]) => cleanup()));
  process.exit(130);
}
export function onTermination(cleanup, phase = 1) {
  cleanupHooks.set(cleanup, { phase });
  if (!listening) { for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) process.once(signal, terminate); listening = true; }
  return () => {
    cleanupHooks.delete(cleanup);
    if (!cleanupHooks.size && listening) { for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) process.off(signal, terminate); listening = false; }
  };
}

export function redact(text, secrets = []) {
  for (const secret of secrets.filter(Boolean).sort((a, b) => b.length - a.length)) text = text.split(secret).join('[REDACTED]');
  return text;
}
// No shell: passwords are child-only environment values, never command arguments.
export function run(file, args = [], { cwd, env = {}, secrets = [], allowed = [0], timeout = 120000, maxBytes = 8 * 1024 * 1024 } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(file, args, { cwd, env: { ...process.env, ...env }, windowsHide: true, shell: false });
    const removeCleanup = onTermination(() => new Promise(done => { child.once('close', done); child.kill(); }), 0);
    let stdout = '', stderr = '', bytes = 0, failure;
    const stop = reason => { failure = reason; child.kill(); };
    const timer = setTimeout(() => stop('Subprocess timeout'), timeout);
    const collect = key => data => {
      bytes += data.length;
      if (bytes > maxBytes) { stop('Subprocess output limit'); return; }
      if (key === 'stdout') stdout += data.toString(); else stderr += data.toString();
    };
    child.stdout.on('data', collect('stdout')); child.stderr.on('data', collect('stderr'));
    child.on('error', error => { clearTimeout(timer); removeCleanup(); reject(new Error(redact(error.message, secrets))); });
    child.on('close', code => {
      clearTimeout(timer);
      removeCleanup();
      const result = { code, stdout: redact(stdout, secrets), stderr: redact(stderr, secrets) };
      if (failure || !allowed.includes(code)) reject(new Error(redact(`${failure ?? `Subprocess failed (${code}): ${file}`}\n${stdout}\n${stderr}`, secrets)));
      else resolve(result);
    });
  });
}
