// Regenerates Android launcher icons from agent-eye.svg. Modes: (default) write, --dry-run, --check.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { runScript } from '../../script-run/script-run.mjs';
import { compareOutputs, generate, readSvg } from './icon-assets.mjs';

await runScript({ name: 'generate-icons', from: import.meta.url, kind: 'script' }, async log => {
  const check = log.args.includes('--check');
  const unknown = log.args.filter(a => a !== '--check');
  if (unknown.length) throw new Error(`Unknown arguments: ${unknown.join(' ')}`);
  log.step('parse and render agent-eye.svg');
  const outputs = await generate(await readSvg(log.root));
  if (log.dryRun) {
    log.step('dry run: nothing written');
    log.echo(JSON.stringify({ dryRun: true, outputs: outputs.map(o => o.path) }));
    return;
  }
  if (check) {
    const drift = await compareOutputs(log.root, outputs);
    if (drift.length) throw new Error(`Icon assets are out of date (run: npm run icons):\n${drift.map(d => `  ${d.path}: ${d.reason}`).join('\n')}`);
    log.step(`${outputs.length} outputs match`);
    return;
  }
  for (const { path, bytes } of outputs) {
    log.step(`write ${path}`);
    const target = join(log.root, path);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, bytes);
  }
});
