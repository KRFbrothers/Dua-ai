import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat, providerConfiguration } from './providers.mjs';

const providers = [
  ['ChatGPT', 'OPENAI', 'responses'],
  ['Claude', 'ANTHROPIC', 'anthropic'],
  ['Grok', 'GROK', 'compatible'],
  ['Kimi', 'KIMI', 'compatible']
];
const types = ['text/plain', 'image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
const samples = {
  'image/jpeg': Buffer.from([255, 216, 255, 224]),
  'image/png': Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  'image/webp': Buffer.from('RIFFxxxxWEBP'),
  'application/pdf': Buffer.from('%PDF-1.7\nfixture'),
  'text/plain': Buffer.from('Namaste\nUTF-8 note: café')
};
const file = (mimeType, name = 'fixture') => ({ name, mimeType, data: samples[mimeType].toString('base64') });
const environment = (prefix, enabledTypes) => ({ [`${prefix}_API_KEY`]: 'mock-private-key', [`${prefix}_MODEL`]: 'mock-model', ...(enabledTypes ? { [`${prefix}_ATTACHMENT_TYPES`]: enabledTypes.join(',') } : {}) });
const request = (model, attachments, signal) => new Request('https://dua.example/api/chat', {
  method: 'POST', signal, body: JSON.stringify({ model, attachments, memory: 'Prefer short replies', messages: [
    { role: 'user', text: 'Earlier prompt' }, { role: 'model', text: 'Earlier answer' }, { role: 'user', text: 'Describe these files' }
  ] })
});
const reply = type => Response.json(type === 'responses' ? { output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Reply' }] }] } : type === 'anthropic' ? { content: [{ type: 'text', text: 'Reply' }] } : { choices: [{ message: { content: 'Reply' } }] });
const noFetch = () => { throw new Error('Unexpected upstream request'); };
const history = (body, type) => type === 'responses' ? body.input : type === 'compatible' ? body.messages.slice(1) : body.messages;

test('capabilities default to TXT and only publish supported explicit MIME opt-ins', () => {
  for (const [name, prefix, type] of providers) {
    const defaults = providerConfiguration(environment(prefix)).find(p => p.name === name);
    assert.equal(defaults.supportsAttachments, true);
    assert.deepEqual(defaults.attachmentTypes, ['text/plain']);
    const opted = providerConfiguration(environment(prefix, [...types, 'image/*', 'application/zip', 'mock-private-key', 'image/png'])).find(p => p.name === name);
    assert.deepEqual(opted.attachmentTypes, type === 'compatible' ? types.filter(t => t !== 'application/pdf') : types);
    assert.ok(!JSON.stringify(opted).includes('mock-private-key'));
  }
  for (const name of ['Dua', 'Gemini']) assert.deepEqual(providerConfiguration().find(p => p.name === name).attachmentTypes, types);
});

for (const [name, prefix, type] of providers) {
  test(`${name} sends UTF-8 TXT only in the final user turn`, async () => {
    let body;
    const response = await handleChat(request(name, [file('text/plain', 'notes.txt')]), { env: environment(prefix), fetcher: async (_url, options) => {
      body = JSON.parse(options.body);
      assert.equal(options.redirect, 'error');
      assert.ok(options.signal instanceof AbortSignal);
      return reply(type);
    } });
    assert.deepEqual(await response.json(), { text: 'Reply' });
    const messages = history(body, type);
    assert.deepEqual(messages.slice(0, 2), [{ role: 'user', content: 'Earlier prompt' }, { role: 'assistant', content: 'Earlier answer' }]);
    assert.deepEqual(messages[2].content, [
      { type: type === 'responses' ? 'input_text' : 'text', text: 'Describe these files' },
      { type: type === 'responses' ? 'input_text' : 'text', text: 'Attached text file (notes.txt):\nNamaste\nUTF-8 note: café' }
    ]);
    assert.match(type === 'responses' ? body.instructions : type === 'anthropic' ? body.system : body.messages[0].content, /Prefer short replies/);
    if (type === 'responses') assert.equal(body.store, false);
  });

  test(`${name} uses its own image schema after model capability opt-in`, async () => {
    for (const mimeType of types.filter(t => t.startsWith('image/'))) {
      const attachment = file(mimeType, 'photo');
      let body;
      const response = await handleChat(request(name, [file('text/plain', 'notes.txt'), attachment]), { env: environment(prefix, [mimeType]), fetcher: async (_url, options) => {
        body = JSON.parse(options.body);
        return reply(type);
      } });
      assert.equal(response.status, 200);
      const content = history(body, type).at(-1).content;
      assert.equal(content[2].text, 'Attached file: photo');
      const dataUrl = `data:${mimeType};base64,${attachment.data}`;
      assert.deepEqual(content.at(-1), type === 'responses' ? { type: 'input_image', image_url: dataUrl } : type === 'anthropic' ? { type: 'image', source: { type: 'base64', media_type: mimeType, data: attachment.data } } : { type: 'image_url', image_url: { url: dataUrl } });
      assert.equal(JSON.stringify(body).split(attachment.data).length - 1, 1);
    }
  });

  test(`${name} rejects disabled image types before calling upstream`, async () => {
    const response = await handleChat(request(name, [file('image/png')]), { env: environment(prefix), fetcher: noFetch });
    assert.equal(response.status, 415);
    assert.match((await response.json()).error, /current configuration/);
    const wrongType = await handleChat(request(name, [file('image/png')]), { env: environment(prefix, ['image/jpeg']), fetcher: noFetch });
    assert.equal(wrongType.status, 415);
  });

  test(`${name} validates attachment bytes, count, size and UTF-8 before upstream`, async () => {
    const txt = file('text/plain');
    const cases = [
      [[{ ...txt, data: '***=' }], 400],
      [[{ ...txt, data: '' }], 400],
      [[{ ...txt, name: '' }], 400],
      [[{ ...txt, name: 'x'.repeat(201) }], 400],
      [[{ ...txt, data: Buffer.from([255]).toString('base64') }], 400],
      [[{ ...txt, data: Buffer.alloc(65537, 65).toString('base64') }], 413],
      [[txt, txt, txt, txt], 400],
      [[{ ...file('image/png'), data: Buffer.from('fake image').toString('base64') }], 400],
      [[{ ...txt, mimeType: 'application/zip' }], 415],
      [[{ ...file('image/jpeg'), data: Buffer.concat([samples['image/jpeg'], Buffer.alloc(1024 * 1024)]).toString('base64') }, { ...file('image/jpeg'), data: Buffer.concat([samples['image/jpeg'], Buffer.alloc(1024 * 1024)]).toString('base64') }], 413]
    ];
    for (const [attachments, status] of cases) assert.equal((await handleChat(request(name, attachments), { env: environment(prefix, types), fetcher: noFetch })).status, status);
  });
}

for (const [name, prefix, type] of providers.filter(p => p[2] !== 'compatible')) {
  test(`${name} encodes multiple PDFs correctly alongside TXT`, async () => {
    const pdf = file('application/pdf', 'first.pdf');
    const second = { ...pdf, name: 'second.pdf', data: Buffer.from('%PDF-1.7\nsecond fixture').toString('base64') };
    let body;
    const response = await handleChat(request(name, [pdf, file('text/plain', 'notes.txt'), second]), { env: environment(prefix, ['application/pdf']), fetcher: async (_url, options) => {
      body = JSON.parse(options.body);
      return reply(type);
    } });
    assert.equal(response.status, 200);
    const content = history(body, type).at(-1).content;
    if (type === 'responses') {
      assert.deepEqual(content.filter(p => p.type === 'input_file'), [pdf, second].map(p => ({ type: 'input_file', filename: p.name, file_data: `data:application/pdf;base64,${p.data}` })));
      assert.equal(body.store, false);
    } else assert.deepEqual(content.filter(p => p.type === 'document'), [pdf, second].map(p => ({ type: 'document', source: { type: 'base64', media_type: 'application/pdf', data: p.data } })));
  });
}

test('Grok and Kimi reject PDF even when the environment requests it', async () => {
  for (const [name, prefix] of providers.filter(p => p[2] === 'compatible')) assert.equal((await handleChat(request(name, [file('application/pdf')]), { env: environment(prefix, types), fetcher: noFetch })).status, 415);
});

test('attachment replies are discarded after cancellation and provider errors stay sanitized', async () => {
  const controller = new AbortController();
  const response = await handleChat(request('ChatGPT', [file('text/plain')], controller.signal), { env: environment('OPENAI'), fetcher: async () => {
    controller.abort();
    return reply('responses');
  } });
  assert.equal(response.status, 504);
  const failure = await handleChat(request('Claude', [file('text/plain')]), { env: environment('ANTHROPIC'), fetcher: async () => Response.json({ error: 'mock-private-key' }, { status: 429, headers: { 'Retry-After': '60' } }) });
  assert.equal(failure.status, 429);
  assert.equal(failure.headers.get('retry-after'), '60');
  assert.ok(!(await failure.text()).includes('mock-private-key'));
});
