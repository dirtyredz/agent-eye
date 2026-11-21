// Leaf: signing credentials env name, public certificate pin check, unsigned-archive check.
import { hex, requireThat } from './assert.mjs';
import { APPLICATION_ID } from './release-identity.mjs';

export const PASS_ENV = 'AGENT_EYE_KEY_PASSWORD';
export function assertJarUnsigned(entries, message = 'Signed JAR input refused') {
  requireThat(!/^META-INF\/[^\r\n]+\.(SF|RSA|DSA|EC)$/im.test(entries), message);
}
export function certificate(pin) {
  requireThat(pin.applicationId === APPLICATION_ID && hex(pin.sha256) && pin.alias === 'agent-eye' && Number.isInteger(pin.firstVersionCode) && pin.firstVersionCode > 0, 'Public certificate pin is not provisioned or invalid');
  return pin;
}
