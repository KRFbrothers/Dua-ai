// Read bounded streams instead of allocating arbitrary request/provider bodies.
export async function readJsonLimited(input, maximumBytes) {
  const length = input.headers.get('content-length');
  if (length && /^\d+$/.test(length) && Number(length) > maximumBytes) throw Object.assign(new Error('Body too large.'), { status: 413 });
  const reader = input.body?.getReader();
  if (!reader) throw new Error('Invalid JSON.');
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maximumBytes) {
        await reader.cancel();
        throw Object.assign(new Error('Body too large.'), { status: 413 });
      }
      chunks.push(value);
    }
    const combined = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { combined.set(chunk, offset); offset += chunk.byteLength; }
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(combined));
  } finally { reader.releaseLock(); }
}

