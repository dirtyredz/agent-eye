// Leaf vocabulary both releases/ and signing/ need: application identity, version shape,
// artifact identity, the public certificate pin and the unsigned-JAR check. Imports only assert.mjs.
import { hex, requireThat } from './assert.mjs';

export const APPLICATION_ID = 'local.agenteye';
export const SDK = Object.freeze({ minSdk: 24, targetSdk: 36, compileSdk: 36 });
export const PASS_ENV = 'AGENT_EYE_KEY_PASSWORD';
export function assertJarUnsigned(entries, message = 'Signed JAR input refused') {
  requireThat(!/^META-INF\/[^\r\n]+\.(SF|RSA|DSA|EC)$/im.test(entries), message);
}
export function version(value, tag = `v${value?.versionName}`) {
  requireThat(value && /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value.versionName), 'Invalid versionName');
  requireThat(Number.isInteger(value.versionCode) && value.versionCode > 0 && value.versionCode <= 2100000000, 'Invalid versionCode');
  requireThat(tag === `v${value.versionName}`, 'Tag/version mismatch');
  return value;
}
export function readVersion(text) {
  const lines = text.trim().split(/\r?\n/);
  requireThat(lines.length === 2 && lines[0].startsWith('versionName=') && /^versionCode=[1-9]\d*$/.test(lines[1]), 'Invalid version.properties');
  return version({ versionName: lines[0].slice(12), versionCode: Number(lines[1].slice(12)) });
}
export function identity(actual, expected) {
  for (const [key, val] of Object.entries({ applicationId: APPLICATION_ID, ...SDK, ...version(expected) })) {
    requireThat(actual[key] === val, `Artifact identity mismatch: ${key}`);
  }
  requireThat(actual.debuggable === false, 'Debuggable artifact refused');
}
export function certificate(pin) {
  requireThat(pin.applicationId === APPLICATION_ID && hex(pin.sha256) && pin.alias === 'agent-eye' && Number.isInteger(pin.firstVersionCode) && pin.firstVersionCode > 0, 'Public certificate pin is not provisioned or invalid');
  return pin;
}
