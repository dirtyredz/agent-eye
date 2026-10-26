import { createClient } from './client.mjs';
import { createStore } from './snapshot-store.mjs';
import { runScript } from '../script-run/script-run.mjs';

// Runs from any directory: the snapshot store is rooted by this file's location.
// --dry-run (anywhere, via log.dryRun) validates the command and prints the plan without touching the phone or disk.
async function main(log) {
  const { dryRun } = log;
  const [command, flag, base, ...extra] = log.args;
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

await runScript({ name: 'snapshot', from: import.meta.url, secrets: ['AGENT_EYE_TOKEN'] }, main);
