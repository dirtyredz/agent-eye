import { join } from 'node:path';
import { run as defaultRun } from './process.mjs';
import { apkTools } from '../signing/apk-signing.mjs';
import { bundleTools } from '../signing/bundle-signing.mjs';

export const BUILD_TOOLS_VERSION = '35.0.0';
export function tooling(root, run = defaultRun) {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? join(process.env.LOCALAPPDATA ?? '', 'Android/Sdk');
  return { apk: apkTools(join(sdk, 'build-tools', BUILD_TOOLS_VERSION), run), bundle: bundleTools(join(root, 'android/build/release-tools'), run) };
}
