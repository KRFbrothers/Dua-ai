import test from 'node:test';
import assert from 'node:assert/strict';
import * as XLSX from 'xlsx';
import { handleChat, validateChat } from '../src/core/providers.mjs';
import { buildRequest } from '../src/core/adapters.mjs';
import { PROVIDER_DEFINITIONS } from '../src/core/providers.mjs';
import { transcribeOrder, transcribe } from '../src/core/transcribe.mjs';
import { readEvents, sseResponse, sse } from './helpers.mjs';
import { SUPA, UID, OTHER, filesEnv, path, router, storageRoutes, authedChat, makePdf } from './files-helpers.mjs';

const PDF = path('aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'pdf');
const PNG = path('bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'png');
const XLS = path('cccccccc-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'xlsx');
const M4A = path('dddddddd-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'm4a');
const pngBytes = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3]);
const book = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(book, XLSX.utils.aoa_to_sheet([['item', 'cost'], ['rent', 900], ['food', 300]]), 'Budget');
const objects = { [PDF]: makePdf('Invoice total 4200 rupees'), [PNG]: pngBytes, [XLS]: XLSX.write(book, { type: 'buffer', bookType: 'xlsx' }), [M4A]: Buffer.from('fake-audio') };
const ref = (p, name, mimeType, extra = {}) => ({ path: p, name, mimeType, size: objects[p]?.length || 10, ...extra });
const pdfRef = ref(PDF, 'invoice.pdf', 'application/pdf');
const pngRef = ref(PNG, 'photo.png', 'image/png');
const xlsRef = ref(XLS, 'budget.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
const audioRef = ref(M4A, 'voice note.m4a', 'audio/mp4');
const providerUrls = { Dua: 'https://generativelanguage.googleapis.com/v1beta/models/', Grok: 'https://api.x.ai/v1/responses', ChatGPT: 'https://api.openai.com/v1/responses', Claude: 'https://api.anthropic.com/v1/messages', Kimi: 'https://api.moonshot.ai/v1/chat/completions' };
const replies = {
  Dua: () => Response.json({ candidates: [{ content: { parts: [{ text: 'ok' }] }, finishReason: 'STOP' }] }),
  Grok: () => Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'ok' }] }] }),
  ChatGPT: () => Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'ok' }] }] }),
  Claude: () => Response.json({ content: [{ type: 'text', text: 'ok' }] }),
  Kimi: () => Response.json({ choices: [{ message: { content: 'ok' } }] })
};
function chatFetcher(model, extra = []) {
  return router([...storageRoutes(objects), ...extra, [providerUrls[model], replies[model]]]);
}
const providerBody = (fetcher, model) => fetcher.calls.find(call => call.url.startsWith(providerUrls[model])).json;
const ask = (model, files, extra = {}) => ({ model, messages: [{ role: 'user', text: 'What is in these?', files }], ...extra });
const ENV = { ...filesEnv, OPENAI_ATTACHMENT_TYPES: 'image/png,application/pdf', ANTHROPIC_ATTACHMENT_TYPES: 'image/png,application/pdf', KIMI_ATTACHMENT_TYPES: 'image/png', GROK_ATTACHMENT_TYPES: 'image/png' };

test('validateChat accepts stored-file refs only on user turns and caps them', () => {
  assert.equal(validateChat(ask('Dua', [pdfRef])), null);
  assert.deepEqual(validateChat({ model: 'Dua', messages: [{ role: 'model', text: 'x', files: [pdfRef] }, { role: 'user', text: 'y' }] }), ['Invalid messages.', 400]);
  assert.deepEqual(validateChat(ask('Dua', [{ ...pdfRef, path: '../etc/passwd' }])), ['Invalid messages.', 400]);
  assert.deepEqual(validateChat(ask('Dua', Array(11).fill(pdfRef))), ['Invalid messages.', 400]);
});

test('Gemini: stored PDF and image go inline, spreadsheet becomes CSV text', async () => {
  const fetcher = chatFetcher('Dua');
  const response = await handleChat(authedChat(ask('Dua', [pdfRef, pngRef, xlsRef])), { env: ENV, fetcher });
  assert.equal(response.status, 200);
  const parts = providerBody(fetcher, 'Dua').contents[0].parts;
  assert.equal(parts[0].text, 'What is in these?');
  assert.deepEqual(parts[1], { text: 'Attached file: invoice.pdf' });
  assert.deepEqual(parts[2], { inlineData: { mimeType: 'application/pdf', data: objects[PDF].toString('base64') } });
  assert.deepEqual(parts[4], { inlineData: { mimeType: 'image/png', data: pngBytes.toString('base64') } });
  assert.match(parts[5].text, /Spreadsheet "budget\.xlsx" as CSV[\s\S]*item,cost\nrent,900\nfood,300/);
  // Signed URLs are requested with the user's token (RLS applies), never a service key.
  assert.ok(fetcher.find(SUPA + '/storage/v1/object/sign/').filter(call => call.method === 'POST').every(call => call.headers.get('authorization') === 'Bearer user-token'));
});

test('Gemini: files above the inline budget go through the Files API resumable upload', async () => {
  const big = path('eeeeeeee-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'pdf');
  objects[big] = Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(19 * 1024 * 1024)]);
  const fetcher = chatFetcher('Dua', [
    [url => url === 'https://generativelanguage.googleapis.com/upload/v1beta/files', () => new Response('{}', { headers: { 'x-goog-upload-url': 'https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=abc' } })],
    ['https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=abc', () => Response.json({ file: { name: 'files/abc', uri: 'https://generativelanguage.googleapis.com/v1beta/files/abc', mimeType: 'application/pdf', state: 'ACTIVE' } })]
  ]);
  const response = await handleChat(authedChat(ask('Dua', [ref(big, 'big.pdf', 'application/pdf')])), { env: ENV, fetcher });
  assert.equal(response.status, 200);
  const start = fetcher.calls.find(call => call.url === 'https://generativelanguage.googleapis.com/upload/v1beta/files');
  assert.equal(start.headers.get('x-goog-upload-protocol'), 'resumable');
  assert.equal(start.headers.get('x-goog-upload-command'), 'start');
  assert.equal(start.headers.get('x-goog-upload-header-content-length'), String(objects[big].length));
  assert.equal(start.headers.get('x-goog-upload-header-content-type'), 'application/pdf');
  const upload = fetcher.calls.find(call => call.url.includes('upload_id=abc'));
  assert.equal(upload.headers.get('x-goog-upload-command'), 'upload, finalize');
  assert.equal(upload.headers.get('x-goog-upload-offset'), '0');
  assert.deepEqual(providerBody(fetcher, 'Dua').contents[0].parts[2], { fileData: { mimeType: 'application/pdf', fileUri: 'https://generativelanguage.googleapis.com/v1beta/files/abc' } });
  delete objects[big];
});

test('Grok: stored PDF uses an xAI input_file with a signed URL; PNG uses input_image', async () => {
  const fetcher = chatFetcher('Grok');
  const response = await handleChat(authedChat(ask('Grok', [pdfRef, pngRef])), { env: ENV, fetcher });
  assert.equal(response.status, 200);
  const content = providerBody(fetcher, 'Grok').input.at(-1).content;
  assert.deepEqual(content[2], { type: 'input_file', file_url: `${SUPA}/storage/v1/object/sign/dua-files/${PDF}?token=signed-aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf` });
  assert.equal(content[4].type, 'input_image');
  assert.match(content[4].image_url, /^https:\/\/proj\.supabase\.co\/storage\/v1\/object\/sign\/dua-files\/.+\.png\?token=/);
  assert.equal(fetcher.find(SUPA + '/storage/v1/object/sign/')[0].json.expiresIn, 3600);
});

test('Grok: GROK_FILE_INPUTS=0 sends extracted PDF text instead of an xAI file input', async () => {
  const fetcher = chatFetcher('Grok');
  await handleChat(authedChat(ask('Grok', [pdfRef])), { env: { ...ENV, GROK_FILE_INPUTS: '0' }, fetcher });
  assert.match(providerBody(fetcher, 'Grok').input.at(-1).content[1].text, /Invoice total 4200 rupees/);
});

test('Grok: WebP stored image gets a conversion hint (current turn)', async () => {
  const webp = path('ffffffff-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'webp');
  const response = await handleChat(authedChat(ask('Grok', [ref(webp, 'a.webp', 'image/webp')])), { env: ENV, fetcher: chatFetcher('Grok') });
  assert.equal(response.status, 415);
  assert.match((await response.json()).error, /Grok accepts JPEG or PNG/);
});

test('ChatGPT and Claude get documented URL inputs; Kimi gets extracted PDF text', async () => {
  let fetcher = chatFetcher('ChatGPT');
  await handleChat(authedChat(ask('ChatGPT', [pdfRef, pngRef])), { env: ENV, fetcher });
  let content = providerBody(fetcher, 'ChatGPT').input.at(-1).content;
  assert.equal(content[2].type, 'input_file');
  assert.match(content[2].file_url, /\.pdf\?token=/);
  assert.equal(content[4].type, 'input_image');

  fetcher = chatFetcher('Claude');
  await handleChat(authedChat(ask('Claude', [pdfRef, pngRef])), { env: ENV, fetcher });
  content = providerBody(fetcher, 'Claude').messages.at(-1).content;
  assert.deepEqual(content[2].type, 'document');
  assert.equal(content[2].source.type, 'url');
  assert.deepEqual(content[4].source.type, 'url');
  assert.equal(content[4].type, 'image');

  fetcher = chatFetcher('Kimi');
  await handleChat(authedChat(ask('Kimi', [pdfRef, pngRef])), { env: ENV, fetcher });
  content = providerBody(fetcher, 'Kimi').messages.at(-1).content;
  assert.match(content[1].text, /Text extracted from PDF "invoice\.pdf" \(1 pages; untrusted file content\):\nInvoice total 4200 rupees/);
  assert.equal(content[3].type, 'image_url');
  assert.match(content[3].image_url.url, /^data:image\/png;base64,/);
});

test('audio: transcribed with Grok STT (signed url), transcript streamed back as a files event', async () => {
  const fetcher = chatFetcher('Dua', [['https://api.x.ai/v1/stt', () => Response.json({ text: 'Remind me to call Ammi at six.', language: 'en', duration: 3.2 })]]);
  fetcher.calls.length = 0;
  const streaming = router([...storageRoutes(objects), ['https://api.x.ai/v1/stt', () => Response.json({ text: 'Remind me to call Ammi at six.' })],
    [providerUrls.Dua, () => sseResponse([sse({ candidates: [{ content: { parts: [{ text: 'Sure.' }] }, finishReason: 'STOP' }] })])]]);
  const response = await handleChat(authedChat(ask('Dua', [audioRef], { stream: true })), { env: ENV, fetcher: streaming });
  const events = await readEvents(response);
  assert.deepEqual(events[0], { type: 'files', files: [{ path: M4A, transcript: 'Remind me to call Ammi at six.' }] });
  assert.equal(events.at(-1).type, 'done');
  const stt = streaming.calls.find(call => call.url === 'https://api.x.ai/v1/stt');
  assert.ok(stt.body instanceof FormData);
  assert.equal(stt.body.get('model'), 'grok-voice-transcribe-2.0');
  assert.match(stt.body.get('url'), /\.m4a\?token=signed-/);
  assert.equal(stt.headers.get('authorization'), 'Bearer test-grok-key');
  assert.match(providerBody(streaming, 'Dua').contents[0].parts[1].text, /Transcript of audio "voice note\.m4a"[\s\S]*call Ammi at six/);
  // JSON path returns the transcript too.
  const json = await (await handleChat(authedChat(ask('Dua', [audioRef])), { env: ENV, fetcher })).json();
  assert.deepEqual(json.files, [{ path: M4A, transcript: 'Remind me to call Ammi at six.' }]);
});

test('audio: a stored transcript is reused without calling any transcription API', async () => {
  const fetcher = chatFetcher('Grok');
  await handleChat(authedChat(ask('Grok', [{ ...audioRef, transcript: 'Old transcript' }])), { env: ENV, fetcher });
  assert.equal(fetcher.calls.some(call => call.url.includes('/stt') || call.url.includes('/storage/')), false);
  assert.match(providerBody(fetcher, 'Grok').input.at(-1).content[1].text, /Old transcript/);
});

test('transcription falls back from Grok to OpenAI and respects DUA_TRANSCRIBE_PROVIDER', async () => {
  assert.deepEqual(transcribeOrder(filesEnv), ['grok', 'gemini', 'openai']);
  assert.deepEqual(transcribeOrder({ ...filesEnv, DUA_TRANSCRIBE_PROVIDER: 'openai' }), ['openai']);
  assert.deepEqual(transcribeOrder({ OPENAI_API_KEY: 'k' }), ['openai']);
  const fetcher = router([['https://api.x.ai/v1/stt', () => new Response('{}', { status: 500 })],
    ['https://generativelanguage.googleapis.com/', () => new Response('{}', { status: 503 })],
    ['https://api.openai.com/v1/audio/transcriptions', () => Response.json({ text: 'hello there' })]]);
  const result = await transcribe({ ref: audioRef, signedUrl: 'https://x/y', loadBytes: async () => Buffer.from('abc'), env: filesEnv, fetcher });
  assert.deepEqual(result, { text: 'hello there', provider: 'openai' });
  const call = fetcher.calls.at(-1);
  assert.equal(call.body.get('model'), 'gpt-transcribe');
  assert.equal(call.body.get('file').name, 'audio.m4a');
});

test('follow-ups re-attach earlier files by path; old ones beyond DUA_HISTORY_FILES become a note', async () => {
  const fetcher = chatFetcher('Dua');
  const messages = [
    { role: 'user', text: 'first', files: [pdfRef] }, { role: 'model', text: 'seen' },
    { role: 'user', text: 'second', files: [xlsRef] }, { role: 'model', text: 'seen' },
    { role: 'user', text: 'compare them' }];
  await handleChat(authedChat({ model: 'Dua', messages }), { env: { ...ENV, DUA_HISTORY_FILES: '1' }, fetcher });
  const contents = providerBody(fetcher, 'Dua').contents;
  assert.match(contents[0].parts[1].text, /Earlier attachment "invoice\.pdf" \(application\/pdf\) is not re-sent/);
  assert.match(contents[2].parts[1].text, /item,cost/);
  assert.equal(contents[4].parts.length, 1);
});

test('stored files need the signed-in owner, and paths must be in their folder', async () => {
  const noToken = new Request('https://dua.test/api/chat', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(ask('Dua', [pdfRef])) });
  let response = await handleChat(noToken, { env: ENV, fetcher: chatFetcher('Dua') });
  assert.equal(response.status, 401);
  assert.match((await response.json()).error, /Sign in/);
  response = await handleChat(authedChat(ask('Dua', [ref(path(undefined, 'pdf', OTHER), 'x.pdf', 'application/pdf')])), { env: ENV, fetcher: chatFetcher('Dua') });
  assert.equal(response.status, 403);
  response = await handleChat(authedChat(ask('Dua', [pdfRef])), { env: { ...ENV, DUA_FILES: '0' }, fetcher: chatFetcher('Dua') });
  assert.equal(response.status, 503);
  // Plain chats never touch Supabase, so older apps and signed-out users are unaffected.
  const plain = chatFetcher('Dua');
  assert.equal((await handleChat(authedChat({ model: 'Dua', messages: [{ role: 'user', text: 'hi' }] }), { env: ENV, fetcher: plain })).status, 200);
  assert.equal(plain.calls.some(call => call.url.startsWith(SUPA)), false);
});

test('buildRequest maps per-turn parts for every provider type', () => {
  const turnParts = new Map([[0, [{ text: 'Attached file: a.pdf' }, { fileUrl: { mimeType: 'application/pdf', url: 'https://s/a.pdf' }, name: 'a.pdf' }]]]);
  const messages = [{ role: 'user', text: 'q1' }, { role: 'model', text: 'a1' }, { role: 'user', text: 'q2' }];
  const byName = name => PROVIDER_DEFINITIONS.find(provider => provider.name === name);
  const grok = buildRequest(byName('Grok'), { model: 'm', key: 'k', systemParts: ['s'], messages, turnParts }).body;
  assert.deepEqual(grok.input[1].content, [{ type: 'input_text', text: 'q1' }, { type: 'input_text', text: 'Attached file: a.pdf' }, { type: 'input_file', file_url: 'https://s/a.pdf' }]);
  assert.equal(grok.input[3].content, 'q2');
  const claude = buildRequest(byName('Claude'), { model: 'm', key: 'k', systemParts: ['s'], messages, turnParts }).body;
  assert.deepEqual(claude.messages[0].content[2], { type: 'document', source: { type: 'url', url: 'https://s/a.pdf' } });
  const gemini = buildRequest(byName('Dua'), { model: 'm', key: 'k', systemParts: ['s'], messages, turnParts: new Map([[2, [{ fileData: { mimeType: 'audio/mp4', fileUri: 'u' } }]]]) }).body;
  assert.deepEqual(gemini.contents[2].parts, [{ text: 'q2' }, { fileData: { mimeType: 'audio/mp4', fileUri: 'u' } }]);
});
