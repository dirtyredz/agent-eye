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
