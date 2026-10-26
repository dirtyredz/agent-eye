import http from 'node:http';
import net from 'node:net';

export function destination(raw) {
  // Validate the original authority before URL can normalize integers/hex/short IPv4.
  if (typeof raw !== 'string' || !/^http:\/\/\d+\.\d+\.\d+\.\d+(?::[1-9]\d{0,4})?\/?$/.test(raw)) {
    throw new Error('Use a literal private IPv4 HTTP base URL, without credentials or a path');
  }
  const url = new URL(raw);
  const host = raw.slice(7).split(/[/:]/)[0];
  if (net.isIP(host) !== 4 || host !== url.hostname) throw new Error('Invalid IPv4 address');
  const [a, b] = host.split('.').map(Number);
  if (!(a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168))) {
    throw new Error('Destination must be RFC1918 IPv4');
  }
  return url;
}

// Check JPEG structure as well as magic: an HTML payload with a JPEG prefix is not a frame.
export function validateJpeg(data) {
  if (data.length < 12 || data.readUInt16BE(0) !== 0xffd8 || data.readUInt16BE(data.length - 2) !== 0xffd9) throw new Error('Invalid JPEG');
  let offset = 2;
  let frame = false;
  while (offset < data.length - 2) {
    if (data[offset++] !== 0xff) throw new Error('Invalid JPEG marker');
    while (data[offset] === 0xff) offset++;
    const marker = data[offset++];
    if (offset + 2 > data.length) throw new Error('Truncated JPEG');
    const length = data.readUInt16BE(offset);
    if (length < 2 || offset + length > data.length - 2) throw new Error('Truncated JPEG segment');
    if ([0xc0, 0xc1, 0xc2].includes(marker)) {
      if (length < 8 || !data.readUInt16BE(offset + 3) || !data.readUInt16BE(offset + 5)) throw new Error('Invalid JPEG dimensions');
      frame = true;
    }
    if (marker === 0xda) {
      if (!frame || length < 6 || offset + length >= data.length - 2) throw new Error('Invalid JPEG scan');
      return;
    }
    offset += length;
  }
  throw new Error('Missing JPEG scan');
}

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
