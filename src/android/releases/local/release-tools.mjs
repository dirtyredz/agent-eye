import { join } from 'node:path';

// Resolves the pinned verification tools (gradle releaseTools) before signing, then stops the Gradle daemon.
// Windows cannot spawn a .bat directly (no shell), so it goes through cmd.exe.
const GRADLE_TIMEOUT_MS = 30 * 60 * 1000;
export async function resolveReleaseTools({ root, run, platform = process.platform }) {
  const androidDir = join(root, 'android');
  const gradle = platform === 'win32'
    ? (args) => run('cmd.exe', ['/c', join(androidDir, 'gradlew.bat'), ...args], { cwd: root, timeout: GRADLE_TIMEOUT_MS })
    : (args) => run(join(androidDir, 'gradlew'), args, { cwd: root, timeout: GRADLE_TIMEOUT_MS });
  try { await gradle(['-p', androidDir, 'releaseTools']); }
  finally { await gradle(['--stop']).catch(() => {}); }
}
