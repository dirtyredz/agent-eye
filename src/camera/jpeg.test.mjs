import test from 'node:test';
import assert from 'node:assert/strict';
import { validateJpeg } from './jpeg.mjs';

// Structural JPEG fixture: SOI, baseline frame, scan, entropy byte, EOI.
const jpeg = Buffer.from('ffd8ffc0000b080001000101011100ffda0008010100003f0000ffd9', 'hex');

test('JPEG validation rejects incomplete frame and scan markers', () => {
  assert.doesNotThrow(() => validateJpeg(jpeg));
  for (let length = 0; length < jpeg.length; length++) assert.throws(() => validateJpeg(jpeg.subarray(0, length)));
});
