// The public signing-certificate pin (signing-certificate.json): path and reader for releases/ callers.
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';

export const pinPathFor = root => join(root, 'src/android/releases/signing-certificate.json');

export async function readPin(pinPath, io = { readFile }) {
  return JSON.parse(await io.readFile(pinPath, 'utf8'));
}

