import { createHash } from 'node:crypto';

export const digest = bytes => ({ size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') });
