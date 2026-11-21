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
