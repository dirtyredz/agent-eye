import http from 'node:http';
import { destination } from './destination.mjs';
import { validateJpeg } from './jpeg.mjs';

export function createClient({ request = http.request, timeoutMs = 10000 } = {}) {
  // `post` (pairing only) sends a tiny JSON body and no bearer token; everything else is an authenticated GET.
  async function fetch(base, token, path, limit, post) {
    const url = destination(base);
    if (!post && (typeof token !== 'string' || !/^[\x21-\x7e]{16,256}$/.test(token))) throw new Error('No session token: run `pair --url ... --code ...` or set AGENT_EYE_TOKEN');
    url.pathname = path;
    return new Promise((resolve, reject) => {
      let settled = false;
      let req;
      let response;
      const finish = (error, value) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        if (error) { response?.destroy(); req?.destroy(); reject(error); }
        else resolve(value);
      };
      const timer = setTimeout(() => finish(new Error('Request deadline exceeded')), timeoutMs);
      try {
        req = request(url, { method: post ? 'POST' : 'GET', agent: false, maxHeaderSize: 8192,
          headers: post ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(post), Connection: 'close' }
            : { Authorization: `Bearer ${token}`, Connection: 'close' } }, res => {
          response = res;
          res.on('error', () => finish(new Error('Response failed')));
          res.on('aborted', () => finish(new Error('Truncated response')));
          if (res.statusCode !== 200) return finish(new Error(post && res.statusCode === 403 ? 'Pairing code rejected, expired or already used: read the current code on the phone' : `HTTP ${res.statusCode}`));
          const lengthText = res.headers['content-length'];
          if (!/^\d+$/.test(lengthText ?? '') || res.headers['transfer-encoding']) return finish(new Error('Missing or invalid response length'));
          const length = Number(lengthText);
          if (!Number.isSafeInteger(length) || length > limit || length < 1) return finish(new Error('Response size out of bounds'));
          const chunks = [];
          let size = 0;
          res.on('data', chunk => {
            size += chunk.length;
            if (size > limit || size > length) finish(new Error('Response size out of bounds'));
            else chunks.push(chunk);
          });
          res.on('end', () => {
            if (!res.complete || size !== length) return finish(new Error('Truncated response'));
            finish(null, { bytes: Buffer.concat(chunks), headers: res.headers });
          });
        });
        req.on('error', () => finish(new Error('Connection failed')));
        req.end(post);
      } catch { finish(new Error('Connection failed')); }
    });
  }
  return {
    // Exchange the 6-digit code shown on the phone for the session token. Never log the result.
    async pair(base, code) {
      if (typeof code !== 'string' || !/^\d{6}$/.test(code)) throw new Error('Pairing code must be exactly 6 digits');
      const { bytes, headers } = await fetch(base, undefined, '/pair', 256, JSON.stringify({ code }));
      if (headers['content-type'] !== 'application/json') throw new Error('Expected JSON pairing response');
      let token;
      try { token = JSON.parse(bytes.toString('utf8')).token; } catch { /* fall through */ }
      if (typeof token !== 'string' || !/^[0-9a-f]{64}$/.test(token)) throw new Error('Invalid pairing response');
      return token;
    },
    async status(base, token) {
      const { bytes, headers } = await fetch(base, token, '/status', 16384);
      if (headers['content-type'] !== 'application/json') throw new Error('Expected JSON status');
      const status = JSON.parse(bytes.toString('utf8'));
      if (status.protocolVersion !== 1 || typeof status.cameraReady !== 'boolean' || typeof status.sessionReady !== 'boolean') throw new Error('Unsupported status protocol');
      return status;
    },
    async shot(base, token) {
      const { bytes, headers } = await fetch(base, token, '/shot.jpg', 8 * 1024 * 1024);
      if (headers['content-type'] !== 'image/jpeg' || headers['cache-control'] !== 'no-store') throw new Error('Expected uncached JPEG');
      validateJpeg(bytes);
      const capturedAt = Number(headers['x-capture-timestamp']);
      const sequence = Number(headers['x-capture-sequence']);
      if (!Number.isSafeInteger(capturedAt) || capturedAt <= 0 || !Number.isSafeInteger(sequence) || sequence <= 0) throw new Error('Invalid capture metadata');
      return { bytes, capturedAt, sequence };
    }
  };
}
