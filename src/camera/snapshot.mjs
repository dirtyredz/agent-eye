import { createClient } from './client.mjs';
import { createStore } from './snapshot-store.mjs';

async function main() {
  const [command, flag, base, ...extra] = process.argv.slice(2);
  const store = createStore();
  if (command === 'clean' && !flag) { await store.clean(); console.log('Agent Eye snapshots cleaned'); return; }
  if (!['status', 'shot', 'smoke'].includes(command) || flag !== '--url' || !base || extra.length) {
    throw new Error('Usage: node src/camera/snapshot.mjs {status|shot|smoke} --url http://PHONE_IP:8765 | clean');
  }
  const client = createClient();
  const token = process.env.AGENT_EYE_TOKEN;
  if (command !== 'shot') {
    const status = await client.status(base, token);
    if (command === 'status') { console.log(JSON.stringify(status, null, 2)); return; }
    if (!status.sessionReady || !status.cameraReady) throw new Error('Camera session is not ready');
  }
  const shot = await client.shot(base, token);
  const saved = await store.save(shot.bytes);
  console.log(JSON.stringify({ path: saved, capturedAt: shot.capturedAt, sequence: shot.sequence }));
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
