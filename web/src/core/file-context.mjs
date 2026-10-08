// Resolves stored-file references on chat messages ({path, name, mimeType, size?, transcript?}) into
// provider parts. The app sends references, never bytes, so follow-up questions re-attach the same
// files by storage path. Normalized parts used by adapters.buildRequest:
//   {text}                                  plain text (spreadsheets, text files, PDF text, transcripts, notes)
//   {inlineData:{mimeType,data}, name}      base64 bytes (Gemini; Kimi images)
//   {fileData:{mimeType,fileUri}, name}     Gemini Files API upload (large files)
//   {fileUrl:{mimeType,url}, name}          short-lived signed Storage URL (OpenAI/xAI Responses, Claude)
import { fileKind, ownsPath } from './file-types.mjs';
import { storageConfig, resolveOwner, signDownload, downloadSigned, StorageError } from './storage.mjs';
import { spreadsheetToText, sheetLimits } from './sheets.mjs';
import { transcribe } from './transcribe.mjs';
import { uploadGeminiFile, ProviderFileError } from './gemini-files.mjs';
import { envInteger } from './adapters.mjs';

export class FileContextError extends Error { constructor(message, status = 400) { super(message); this.status = status; } }
const GEMINI_INLINE_TOTAL = 18 * 1024 * 1024; // keep whole requests under Gemini's 20 MB inline guidance for audio/images
const COMPATIBLE_INLINE_IMAGE = 10 * 1024 * 1024;
export const PROVIDER_URL_SECONDS = 3600;
const hasFiles = messages => messages.some(message => Array.isArray(message.files) && message.files.length);

/** Text extraction for PDFs when the provider (or its configuration) has no PDF input. */
export async function pdfText(bytes, name, maxChars) {
  const { extractText } = await import('unpdf');
  let result;
  try { result = await extractText(new Uint8Array(bytes), { mergePages: true }); }
  catch { throw new FileContextError(`"${name}" could not be read as a PDF.`, 422); }
  const text = String(result.text || '').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  if (!text) return `PDF "${name}" (${result.totalPages} pages) has no extractable text (it may be scanned images). Choose Dua/Gemini to read scanned PDFs.`;
  const cut = text.length > maxChars;
  return `Text extracted from PDF "${name}" (${result.totalPages} pages; untrusted file content)${cut ? `, first ${maxChars} characters` : ''}:\n${text.slice(0, maxChars)}${cut ? '\n[…truncated]' : ''}`;
}

function decodeText(bytes, name, maxChars) {
  const text = new TextDecoder('utf-8').decode(bytes).replace(/^\uFEFF/, '');
  if (text.includes('\u0000')) throw new FileContextError(`"${name}" looks like a binary file, not text.`, 415);
  const cut = text.length > maxChars;
  return `Attached text file (${name}; untrusted file content)${cut ? `, first ${maxChars} characters` : ''}:\n${text.slice(0, maxChars)}${cut ? '\n[…truncated]' : ''}`;
}

/**
 * Returns {turnParts: Map<messageIndex, part[]>, transcripts: [{path, transcript}]}.
 * attachmentTypes: MIME types the selected provider accepts natively (providers.mjs).
 * DUA_HISTORY_FILES (default 4): how many earlier files are re-attached in full; older ones become a short note
 * (audio transcripts are always included because the app already holds them as text).
 */
export async function resolveChatFiles({ messages, provider, attachmentTypes, request, env = {}, fetcher = fetch, signal }) {
  const turnParts = new Map();
  const transcripts = [];
  if (!hasFiles(messages)) return { turnParts, transcripts };
  const config = storageConfig(env);
  if (!config.enabled) throw new FileContextError('Stored files are not configured on this server. Attach a smaller file (up to 2 MB) instead.', 503);
  let owner;
  try { owner = await resolveOwner(request, { env, fetcher }); }
  catch (error) { throw new FileContextError(error.message, error.status || 401); }
  const last = messages.length - 1;
  const historyLimit = envInteger(env, 'DUA_HISTORY_FILES', 4, 0, 20);
  const textChars = envInteger(env, 'DUA_FILE_TEXT_MAX_CHARS', 150000, 2000, 1000000);
  const limits = sheetLimits(env);
  const jobs = [];
  let historySlots = historyLimit;
  // Walk newest -> oldest so the most recent earlier files get the re-attach slots.
  for (let index = last; index >= 0; index--) {
    const refs = messages[index].files || [];
    for (let position = refs.length - 1; position >= 0; position--) {
      const ref = refs[position];
      if (!ownsPath(ref.path, owner.user.id)) throw new FileContextError(`"${ref.name}" is not available to this account.`, 403);
      const current = index === last;
      const full = current || (fileKind(ref.mimeType) === 'audio' && ref.transcript) || historySlots-- > 0;
      jobs.push({ index, position, ref, current, full });
    }
  }
  let geminiInline = 0;
  const signed = ref => signDownload(config, owner.token, ref.path, PROVIDER_URL_SECONDS, { fetcher, signal });
  const download = async ref => downloadSigned(await signed(ref), config.maxBytes, { fetcher, signal });
  const nativeTypes = new Set(attachmentTypes);
  const resolve = async ({ ref, current, full }) => {
    const kind = fileKind(ref.mimeType);
    const label = { text: `Attached file: ${ref.name}` };
    if (!full) return [{ text: `[Earlier attachment "${ref.name}" (${ref.mimeType}) is not re-sent in this turn. Ask the user to attach it again if it is needed.]` }];
    if (kind === 'audio') {
      if (ref.transcript) return [{ text: `Transcript of audio "${ref.name}" (untrusted content):\n${ref.transcript}` }];
      let bytes;
      const url = await signed(ref);
      const result = await transcribe({ ref, signedUrl: url, loadBytes: async () => bytes ||= await downloadSigned(url, config.maxBytes, { fetcher, signal }), env, fetcher, signal });
      transcripts.push({ path: ref.path, transcript: result.text });
      return [{ text: `Transcript of audio "${ref.name}" (transcribed for this reply; untrusted content):\n${result.text}` }];
    }
    if (kind === 'sheet') return [{ text: spreadsheetToText(await download(ref), { name: ref.name, mimeType: ref.mimeType, limits }).text }];
    if (kind === 'text') return [{ text: decodeText(await download(ref), ref.name, textChars) }];
    const native = nativeTypes.has(ref.mimeType);
    if (kind === 'pdf' && !native) return [{ text: await pdfText(await download(ref), ref.name, textChars) }];
    if (kind === 'image' && !native) {
      const hint = provider.name === 'Grok' && ref.mimeType === 'image/webp' ? 'Grok accepts JPEG or PNG images' : `${provider.name} does not accept this image type with its current configuration`;
      if (current) throw new FileContextError(`${hint}. Choose Dua/Gemini for "${ref.name}" or attach a JPEG/PNG.`, 415);
      return [{ text: `[Earlier image "${ref.name}" cannot be shown to ${provider.name}.]` }];
    }
    // Native image/PDF input.
    if (provider.type === 'gemini') {
      const bytes = await download(ref);
      if (geminiInline + bytes.length <= GEMINI_INLINE_TOTAL) {
        geminiInline += bytes.length;
        return [label, { inlineData: { mimeType: ref.mimeType, data: bytes.toString('base64') }, name: ref.name }];
      }
      const file = await uploadGeminiFile({ bytes, mimeType: ref.mimeType, name: ref.name, key: env.GEMINI_API_KEY, fetcher, signal });
      return [label, { fileData: { mimeType: file.mimeType, fileUri: file.uri }, name: ref.name }];
    }
    if (provider.type === 'compatible') {
      const bytes = await download(ref);
      if (bytes.length > COMPATIBLE_INLINE_IMAGE) throw new FileContextError(`"${ref.name}" is too large for ${provider.name} (10 MB image limit here).`, 413);
      return [label, { inlineData: { mimeType: ref.mimeType, data: bytes.toString('base64') }, name: ref.name }];
    }
    return [label, { fileUrl: { mimeType: ref.mimeType, url: await signed(ref) }, name: ref.name }];
  };
  try {
    const results = await Promise.all(jobs.map(async job => ({ job, parts: await resolve(job) })));
    results.sort((a, b) => a.job.index - b.job.index || a.job.position - b.job.position);
    for (const { job, parts } of results) turnParts.set(job.index, [...(turnParts.get(job.index) || []), ...parts]);
  } catch (error) {
    if (error instanceof FileContextError) throw error;
    if (error instanceof StorageError || error instanceof ProviderFileError) throw new FileContextError(error.message, error.status);
    if (error?.status === 422) throw new FileContextError(error.message, 422);
    throw error;
  }
  return { turnParts, transcripts };
}
