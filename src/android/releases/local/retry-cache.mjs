import { mkdir, access } from 'node:fs/promises';
import { join } from 'node:path';
import { SIGNED_MANIFEST, finalNames } from '../artifacts/contract.mjs';
import { requireThat } from '../../shared/assert.mjs';

export function retryCache({ gh }) {
  return {
    async resume(state) {
      try { await access(join(state.cache, SIGNED_MANIFEST)); return state.cache; }
      catch (e) { if (e.code !== 'ENOENT') throw e; }
      const existing = state.list.filter(a => finalNames.includes(a.name) && a.name !== 'mapping.txt');
      if (!existing.length) return null;
      requireThat(finalNames.every(name => state.list.some(a => a.name === name)), 'Partial upload requires retained local .tmp/releases cache; restore it before retry');
      await mkdir(state.cache, { recursive: true });
      for (const name of finalNames) await gh.download(state.tag, name, state.cache);
      return state.cache;
    },
  };
}
