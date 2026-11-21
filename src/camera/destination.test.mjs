import test from 'node:test';
import assert from 'node:assert/strict';
import { destination } from './destination.mjs';

const base = 'http://192.168.68.20:8765';

test('only canonical literal RFC1918 HTTP destinations are accepted', () => {
  for (const url of [base, 'http://10.0.0.1', 'http://172.16.0.1', 'http://172.31.255.254']) assert.ok(destination(url));
  for (const url of ['https://192.168.1.1', 'http://localhost', 'http://127.0.0.1', 'http://169.254.1.1',
    'http://172.15.1.1', 'http://172.32.0.1', 'http://8.8.8.8', 'http://192.168.1.1@evil.test',
    'http://user:pass@192.168.1.1', 'http://192.168.1.1/shot.jpg', 'http://192.168.1.1?token=x',
    'http://0xc0a80101', 'http://3232235777', 'http://192.168.01.1', 'http://[::ffff:192.168.1.1]', 'http://192.168.1.1:99999']) {
    assert.throws(() => destination(url), undefined, url);
  }
});
