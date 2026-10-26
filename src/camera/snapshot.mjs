import { createClient } from './client.mjs';
import { createStore } from './snapshot-store.mjs';
import { runScript } from '../android/tooling/script-run.mjs';

// Runs from any directory: the snapshot store is rooted by this file's location.
// --dry-run (last argument) validates the command and prints the plan without touching the phone or disk.
async function main(args, log) {
  const dryRun = args.at(-1) === '--dry-run';
  const [command, flag, base, ...extra] = dryRun ? args.slice(0, -1) : args;
  if (command === 'clean' && !flag) {
    if (dryRun) { console.log(JSON.stringify({ mode: 'dry-run', command })); return; }
    log.step('clean snapshots');
    await createStore().clean(); console.log('Agent Eye snapshots cleaned'); return;
  }
  if (!['status', 'shot', 'smoke'].includes(command) || flag !== '--url' || !base || extra.length) {
    throw new Error('Usage: node src/camera/snapshot.mjs {status|shot|smoke} --url http://PHONE_IP:8765 | clean  [--dry-run]');
  }
  if (dryRun) { console.log(JSON.stringify({ mode: 'dry-run', command, url: base })); return; }
  const client = createClient();
  const token = process.env.AGENT_EYE_TOKEN;
  log.addSecrets(token);
  log.step(`${command} ${base}`);
  if (command !== 'shot') {
    const status = await client.status(base, token);
    if (command === 'status') { console.log(JSON.stringify(status, null, 2)); return; }
    if (!status.sessionReady || !status.cameraReady) throw new Error('Camera session is not ready');
  }
  const shot = await client.shot(base, token);
  const saved = await createStore().save(shot.bytes);
  console.log(JSON.stringify({ path: saved, capturedAt: shot.capturedAt, sequence: shot.sequence }));
}

const argv = process.argv.slice(2);
await runScript({ name: 'snapshot', from: import.meta.url, secrets: ['AGENT_EYE_TOKEN'], argv }, log => main(argv, log));
