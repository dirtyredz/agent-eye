import { createClient } from './client.mjs';
import { createStore } from './snapshot-store.mjs';
import { createTokenStore } from './token-store.mjs';
import { USAGE, parse, redaction, resolveTarget } from './snapshot-args.mjs';
import { runScript } from '../script-run/script-run.mjs';

// Runs from any directory: stores are rooted by this repo's location.
// --dry-run (anywhere, via log.dryRun) validates the command and prints the plan without touching the phone or disk.
async function main(log) {
  const { dryRun } = log;
  const args = parse(log.args);
  const { command } = args;
  if (command === 'clean' || command === 'unpair') {
    if (args.url || args.code || args.token) throw new Error(USAGE);
    if (dryRun) { console.log(JSON.stringify({ mode: 'dry-run', command })); return; }
    log.step(command);
    if (command === 'clean') { await createStore().clean(); console.log('Agent Eye snapshots cleaned'); }
    else { await createTokenStore().clear(); console.log('Agent Eye stored token removed'); }
    return;
  }
  if (command === 'pair') {
    if (!args.url || !args.code || args.token) throw new Error(USAGE);
    log.addSecrets(args.code);
    if (dryRun) { console.log(JSON.stringify({ mode: 'dry-run', command, url: args.url })); return; }
    log.step(`pair ${args.url}`);
    const token = await createClient().pair(args.url, args.code);
    log.addSecrets(token);
    const tokens = createTokenStore();
    const { restricted } = await tokens.save({ token, url: args.url });
    console.log(`Paired. Token stored (not shown) at ${tokens.file}`);
    if (!restricted) console.warn('Warning: could not restrict the token file to the current user; protect it manually.');
    return;
  }
  if (!['status', 'shot', 'smoke'].includes(command) || args.code) throw new Error(USAGE);
  if (dryRun) { console.log(JSON.stringify({ mode: 'dry-run', command, url: args.url })); return; }
  const target = await resolveTarget(args);
  if (!target.url) throw new Error(`${USAGE}\nNo --url given and no paired phone found`);
  const client = createClient();
  log.addSecrets(target.token);
  log.step(`${command} ${target.url}`);
  if (command !== 'shot') {
    const status = await client.status(target.url, target.token);
    if (command === 'status') { console.log(JSON.stringify(status, null, 2)); return; }
    if (!status.sessionReady || !status.cameraReady) throw new Error('Camera session is not ready');
  }
  const shot = await client.shot(target.url, target.token);
  const saved = await createStore().save(shot.bytes);
  console.log(JSON.stringify({ path: saved, capturedAt: shot.capturedAt, sequence: shot.sequence }));
}

await runScript({ name: 'snapshot', from: import.meta.url, secrets: ['AGENT_EYE_TOKEN'], redaction }, main);
