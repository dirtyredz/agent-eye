// Owner repair tools for a failed provisioning: list the Agent Eye vault items (never values) and remove
// orphaned ones. Cleanup is allowed only while nothing can depend on the key: the public pin is still unset
// and GitHub has no release at all. Anything uncertain (gh failure, odd responses, duplicates) fails closed.
import { requireThat } from '../releases/artifacts/contract.mjs';

const describe = item => `${item.title} [${item.category}, ${item.state}, created ${item.createdAt instanceof Date ? item.createdAt.toISOString() : item.createdAt}]`;
const isAgentEye = item => typeof item.title === 'string' && item.title.startsWith('Agent Eye ');

export async function inspectVault({ vault, say = console.log }) {
  const items = (await vault.overview()).filter(isAgentEye);
  say(`Agent Eye items in the configured vault (${items.length}); values are never shown:`);
  for (const item of items) say(`  ${describe(item)}`);
  const wanted = [vault.config.documentTitle, vault.config.passwordTitle];
  for (const title of wanted) say(`  ${title}: ${items.filter(i => i.title === title).length ? 'present' : 'absent'}`);
  return items;
}

export async function cleanupOrphans({ vault, pin, gh, execute, say = console.log }) {
  requireThat(pin?.sha256 === null, 'Refusing cleanup: the public certificate pin is set, the key may be in use');
  let releases;
  try { releases = await gh.api('releases?per_page=100'); } catch { releases = undefined; }
  requireThat(Array.isArray(releases), 'Refusing cleanup: could not list GitHub releases (gh failed); fail closed');
  requireThat(releases.length === 0, `Refusing cleanup: ${releases.length} GitHub release(s) exist, a key may have signed one`);
  const titles = [vault.config.documentTitle, vault.config.passwordTitle];
  const all = await vault.overview();
  const targets = [];
  for (const title of titles) {
    const matches = all.filter(item => item.title === title);
    requireThat(matches.length <= 1, `Refusing cleanup: duplicate "${title}" items; resolve in 1Password`);
    targets.push(...matches);
  }
  say(targets.length ? `Orphaned items to delete (${targets.length}):` : 'No orphaned Agent Eye items found; nothing to delete.');
  for (const item of targets) say(`  ${describe(item)}`);
  if (!execute) { say('Dry run: nothing deleted. Rerun with --execute to delete exactly these items.'); return { deleted: 0, targets }; }
  for (const item of targets) await vault.deleteItem(item.id);
  say(`Deleted ${targets.length} item(s). provision can be rerun.`);
  return { deleted: targets.length, targets };
}
