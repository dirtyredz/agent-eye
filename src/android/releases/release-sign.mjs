import { requireThat, certificate } from './contract.mjs';

// The orchestration has no ambient side effects. The production adapter and fixture
// adapters use the same order, including all preflight checks before vault access.
export async function releaseSign(deps, options) {
  const unlock = await deps.lock();
  try {
    const state = await deps.preflight(options);
    certificate(state.pin);
    requireThat(state.manifest.versionCode >= state.pin.firstVersionCode, 'Version precedes certificate enrollment');
    await deps.history(state.manifest);
    const outputs = await deps.resume(state) ?? await deps.withKey(state.pin, key => deps.sign(state, key));
    await deps.verify(state, outputs);
    await deps.recheck(state);
    await deps.upload(state, outputs);
    await deps.verifyDownloads(state, outputs);
    await deps.recheck(state);
    await deps.history(state.manifest);
    await deps.removeUnsigned(state);
    if (options.publish) {
      await deps.recheck(state);
      await deps.history(state.manifest);
      await deps.publish(state);
    }
    return outputs;
  } finally { await unlock(); }
}
