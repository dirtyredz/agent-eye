// The public signing-certificate pin (signing-certificate.json): one place that knows its path, read and write.
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export const pinPathFor = root => join(root, 'src/android/releases/signing-certificate.json');

export async function readPin(pinPath, io = { readFile }) {
  return JSON.parse(await io.readFile(pinPath, 'utf8'));
}

export async function writePin(pinPath, pin, io = { writeFile }) {
  await io.writeFile(pinPath, JSON.stringify(pin, null, 2) + '\n');
}
