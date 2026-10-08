// Stored-file types (Supabase Storage uploads). Inline attachments keep their own list in attachments.mjs.
/** kind: how the backend hands the file to a provider. ext: the only extension used in storage paths. */
const TYPES = [
  ['image/jpeg', 'image', 'jpg'], ['image/png', 'image', 'png'], ['image/webp', 'image', 'webp'],
  ['application/pdf', 'pdf', 'pdf'],
  ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'sheet', 'xlsx'],
  ['application/vnd.ms-excel', 'sheet', 'xls'],
  ['application/vnd.oasis.opendocument.spreadsheet', 'sheet', 'ods'],
  ['text/csv', 'sheet', 'csv'],
  ['text/plain', 'text', 'txt'], ['text/markdown', 'text', 'md'], ['application/json', 'text', 'json'],
  ['audio/mp4', 'audio', 'm4a'], ['audio/m4a', 'audio', 'm4a'], ['audio/x-m4a', 'audio', 'm4a'], ['audio/aac', 'audio', 'aac'],
  ['audio/mpeg', 'audio', 'mp3'], ['audio/mp3', 'audio', 'mp3'], ['audio/wav', 'audio', 'wav'], ['audio/x-wav', 'audio', 'wav'], ['audio/wave', 'audio', 'wav'],
  ['audio/ogg', 'audio', 'ogg'], ['audio/opus', 'audio', 'opus'], ['audio/webm', 'audio', 'webm'], ['audio/flac', 'audio', 'flac']
];
export const STORED_TYPES = Object.freeze(Object.fromEntries(TYPES.map(([mime, kind, ext]) => [mime, Object.freeze({ kind, ext })])));
export const STORED_MIME_TYPES = Object.freeze(TYPES.map(([mime]) => mime));
/** Canonical MIME type per extension, used when a file app reports a vague type such as application/octet-stream. */
const BY_EXTENSION = { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp', pdf: 'application/pdf',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', xls: 'application/vnd.ms-excel', ods: 'application/vnd.oasis.opendocument.spreadsheet',
  csv: 'text/csv', txt: 'text/plain', md: 'text/markdown', json: 'application/json',
  m4a: 'audio/mp4', aac: 'audio/aac', mp3: 'audio/mpeg', wav: 'audio/wav', ogg: 'audio/ogg', oga: 'audio/ogg', opus: 'audio/opus', webm: 'audio/webm', flac: 'audio/flac' };

/** Returns the stored MIME type for a client-reported type and file name, or null when unsupported. */
export function storedType(mimeType, name = '') {
  const type = String(mimeType || '').split(';')[0].trim().toLowerCase();
  if (STORED_TYPES[type]) return type;
  const ext = String(name).toLowerCase().match(/\.([a-z0-9]{1,5})$/)?.[1];
  if (ext && BY_EXTENSION[ext] && (!type || type === 'application/octet-stream' || type.startsWith('application/') || type.startsWith('audio/'))) return BY_EXTENSION[ext];
  return null;
}
export const fileKind = mimeType => STORED_TYPES[mimeType]?.kind || null;

/** Object path inside the bucket: "<owner uuid>/<file uuid>.<ext>". Names stay on the phone, not in storage. */
export const STORED_PATH = /^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.[a-z0-9]{2,5}$/;
export const ownsPath = (path, userId) => typeof path === 'string' && STORED_PATH.exec(path)?.[1] === String(userId || '').toLowerCase();

/** Validates one file reference sent with a chat message: {path, name, mimeType, size?, transcript?}. */
export function validFileRef(ref) {
  if (!ref || typeof ref !== 'object' || Array.isArray(ref)) return false;
  if (Object.keys(ref).some(key => !['path', 'name', 'mimeType', 'size', 'transcript'].includes(key))) return false;
  if (typeof ref.path !== 'string' || !STORED_PATH.test(ref.path)) return false;
  if (typeof ref.name !== 'string' || !ref.name.trim() || ref.name.length > 200) return false;
  if (typeof ref.mimeType !== 'string' || !STORED_TYPES[ref.mimeType]) return false;
  if (ref.size !== undefined && (!Number.isSafeInteger(ref.size) || ref.size < 0)) return false;
  if (ref.transcript !== undefined && (typeof ref.transcript !== 'string' || ref.transcript.length > 100000)) return false;
  return true;
}
