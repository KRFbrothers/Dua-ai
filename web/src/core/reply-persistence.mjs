import { createHash } from 'node:crypto';
import { handleCloudRead } from './account.mjs';
import { validateCloudSnapshot } from './cloud-snapshot.mjs';
import { eventStreamResponse, readSse } from './sse.mjs';
import { readJsonLimited } from './limited-json.mjs';
import { defaultProjects } from './projects.mjs';

export class ReplySaveError extends Error {
  constructor(message, status = 409) { super(message); this.status = status; }
}
const id = value => typeof value === 'string' && value.trim() && value.length <= 100;
const hash = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
const conflict = () => { throw new ReplySaveError('Cloud chat changed. Sync and review before sending again.'); };

/** Versioned, order-independent JSON digest shared with Android; strings retain their UTF-16 units. */
export function messagesHash(messages) {
  const digest = createHash('sha256').update('dua-messages-v1:');
  const add = value => {
    if (value === null) digest.update('z');
    else if (typeof value === 'boolean') digest.update(value ? 't' : 'f');
    else if (typeof value === 'number') {
      if (!Number.isFinite(value)) throw new TypeError('Invalid digest number');
      const bytes = Buffer.alloc(8); bytes.writeDoubleBE(value === 0 ? 0 : value); digest.update('d').update(bytes);
    } else if (typeof value === 'string') digest.update('s' + value.length + ':').update(Buffer.from(value, 'utf16le').swap16());
    else if (Array.isArray(value)) { digest.update('a' + value.length + ':'); value.forEach(add); }
    else {
      const keys = Object.keys(value).sort(); digest.update('o' + keys.length + ':');
      for (const key of keys) { add(key); add(value[key]); }
    }
  };
  add(messages); return digest.digest('hex');
}

export function validateReplySave(data) {
  const value = data.persistence;
  if (value === undefined) return null;
  try {
    if (!value || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).some(key => !['ownerId', 'chatId', 'baseHash', 'prefixHash', 'user', 'replyId', 'replaceReply', 'turnIds', 'submittedDraft'].includes(key)) ||
      data.stream !== true || !id(value.ownerId) || !id(value.chatId) || !id(value.replyId) || !hash(value.baseHash) ||
      typeof value.replaceReply !== 'boolean' || (value.replaceReply && !hash(value.prefixHash)) ||
      typeof value.submittedDraft !== 'string' || value.submittedDraft.length > 8000 ||
      !Array.isArray(value.turnIds) || value.turnIds.length !== data.messages.length || !value.turnIds.every(id) || new Set(value.turnIds).size !== value.turnIds.length ||
      !value.user || value.user.role !== 'user' || value.user.state !== 'complete' || !id(value.user.id) || value.user.id === value.replyId ||
      value.turnIds.at(-1) !== value.user.id || value.user.text !== data.messages.at(-1).text ||
      (data.context && data.context.id !== value.chatId)) throw Error();
    const checked = validateCloudSnapshot({ format: 'dua-cloud-snapshot', version: 1, memory: [], chats: [
      { id: value.chatId, model: data.model, draft: '', updatedAt: 0, messages: [value.user] }
    ] }).chats[0].messages[0];
    if (messagesHash(checked.files || []) !== messagesHash(data.messages.at(-1).files || [])) throw Error();
    value.user = checked;
    return null;
  } catch { return ['Invalid server reply save request.', 400]; }
}

const metadataRequest = (request, signal) => new Request(request.url, { headers: request.headers, signal });
async function readCloud(request, env, fetcher, signal) {
  signal.throwIfAborted();
  const response = await handleCloudRead(metadataRequest(request, signal), {
    url: env.SUPABASE_URL, key: env.SUPABASE_ANON_KEY, allowedEmail: env.DUA_ALLOWED_EMAIL, fetcher
  });
  const data = await response.json();
  if (!response.ok) throw new ReplySaveError(data.error || 'Cloud chat could not be checked.', response.status);
  return data;
}

/** Preflight never writes. Reuse the owner snapshot for project isolation and prompt context. */
export async function prepareReplySave(request, data, { env, fetcher }) {
  if (request.headers.get('x-dua-native') !== '1' || !env.DUA_ALLOWED_EMAIL?.trim()) throw new ReplySaveError('Owner sign-in is required for server reply saving.', 503);
  const token = request.headers.get('authorization')?.match(/^Bearer ([A-Za-z0-9._-]{1,10000})$/)?.[1];
  if (!token) throw new ReplySaveError('Sign in before saving server replies.', 401);
  const cloud = await readCloud(request, env, fetcher, request.signal);
  const save = data.persistence;
  if (cloud.ownerId !== save.ownerId) throw new ReplySaveError('Account changed. Sign in and review cloud history.', 403);
  if (cloud.snapshot.deletedChatIds?.includes(save.chatId)) throw new ReplySaveError('This chat was deleted. Start a new chat.');
  const original = cloud.snapshot.chats.find(chat => chat.id === save.chatId);
  const messages = original?.messages || [];
  const baseHash = messagesHash(messages);
  let replace = save.replaceReply && original?.messages.at(-1)?.id === save.replyId;
  if (replace) {
    if (messages.at(-1).role !== 'model' || messages.at(-2)?.id !== save.user.id ||
      messagesHash([messages.at(-2)]) !== messagesHash([save.user]) || baseHash !== save.baseHash) conflict();
  } else {
    if (baseHash !== (save.replaceReply ? save.prefixHash : save.baseHash) ||
      messages.some(message => message.id === save.user.id || message.id === save.replyId)) conflict();
  }
  if ((original?.projectId || 'personal') !== (data.projectId || 'personal')) conflict();
  if ((data.context?.summary || '') !== (original?.summary?.text || '')) conflict();
  const ids = new Set([...messages.map(message => message.id), save.user.id]);
  if (save.turnIds.some(messageId => !ids.has(messageId))) conflict();
  const throughIds = save.turnIds;
  return {
    cloud,
    async commit(result, signal) {
      // Re-read and merge only this chat, retaining unrelated chats, drafts, memory and deletion IDs.
      for (let attempt = 0; attempt < 2; attempt++) {
        let current;
        try { current = await readCloud(request, env, fetcher, signal); }
        catch (error) {
          // A 401 after generation must not trigger Android's automatic whole-request auth retry.
          if (error.status === 401 || error.status === 403) throw new ReplySaveError('Session changed before server saving. Sign in, sync and review the phone reply.', 503);
          throw error;
        }
        if (current.ownerId !== save.ownerId) conflict();
        const latest = current.snapshot.chats.find(chat => chat.id === save.chatId);
        if (current.snapshot.deletedChatIds?.includes(save.chatId) ||
          Boolean(original) !== Boolean(latest) || messagesHash(latest?.messages || []) !== baseHash ||
          (!original && current.revision !== cloud.revision) ||
          (latest?.projectId || 'personal') !== (data.projectId || 'personal')) conflict();
        const project = (current.snapshot.projects || defaultProjects()).find(item => item.id === (data.projectId || 'personal'));
        if (!project || project.archived) conflict();
        const user = { ...save.user };
        if (user.files) user.files = user.files.map(file => result.transcripts[file.path] ? { ...file, transcript: result.transcripts[file.path] } : file);
        const reply = { id: save.replyId, role: 'model', state: 'complete', text: result.text,
          ...(result.thinking ? { thinking: result.thinking, thinkingSeconds: result.thinkingSeconds } : {}),
          ...(result.codeRuns.length ? { codeRuns: result.codeRuns } : {}),
          ...(result.build ? { build: result.build } : {}), ...(result.proposal ? { proposal: result.proposal } : {}) };
        const nextMessages = (replace ? [...latest.messages.slice(0, -2), user, reply] : [...(latest?.messages || []), user, reply])
          .map(message => message.role === 'user' && message.files ? { ...message, files: message.files.map(file =>
            result.transcripts[file.path] ? { ...file, transcript: result.transcripts[file.path] } : file) } : message);
        const chat = { ...(latest || { id: save.chatId, projectId: data.projectId || 'personal', draft: '', pinned: false }),
          model: data.model, updatedAt: Date.now(), messages: nextMessages };
        if (chat.draft === save.submittedDraft && chat.draft === (original?.draft || '')) chat.draft = '';
        if (data.mode === 'build' && latest?.buildRepository === original?.buildRepository) chat.buildRepository = data.repository;
        if (result.context && JSON.stringify(latest?.summary) === JSON.stringify(original?.summary))
          chat.summary = { text: result.context.summary, throughId: throughIds[result.context.covered - 1] };
        let snapshot;
        try { snapshot = validateCloudSnapshot({ ...current.snapshot, chats: [...current.snapshot.chats.filter(item => item.id !== chat.id), chat] }); }
        catch { throw new ReplySaveError('Reply could not fit safely in cloud history. Export or review history; the phone reply is kept.', 413); }
        const storedChat = snapshot.chats.find(item => item.id === chat.id);
        signal.throwIfAborted();
        const response = await fetcher(env.SUPABASE_URL.trim().replace(/\/$/, '') + '/rest/v1/rpc/dua_write_cloud_snapshot', {
          method: 'POST', redirect: 'error', signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
          headers: { apikey: env.SUPABASE_ANON_KEY.trim(), Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
          body: JSON.stringify({ expected_revision: current.revision, next_payload: snapshot })
        });
        if (!response.ok) throw new ReplySaveError('Reply was generated but server saving could not be confirmed. Sync and review before retrying.', 503);
        const saved = await readJsonLimited(response, 16384);
        if (saved.conflict === true) { if (attempt === 0) continue; conflict(); }
        if (!Number.isSafeInteger(saved.revision) || saved.revision <= current.revision || saved.revision > 2147483647) throw new ReplySaveError('Server save response was invalid. Sync and review before retrying.', 503);
        const { messages: _messages, ...chatMetadata } = storedChat;
        return { ownerId: save.ownerId, chatId: chat.id, replyId: save.replyId, userId: user.id, revision: saved.revision,
          chat: chatMetadata, replyHash: messagesHash([storedChat.messages.at(-1)]),
          original: { title: original?.title || null, pinned: original?.pinned || false, buildRepository: original?.buildRepository || null },
          ...(reply.thinking ? { thinkingSeconds: reply.thinkingSeconds } : {}) };
      }
    }
  };
}

/** Completion is withheld until the existing revision-checked RPC acknowledges the server write. */
export function savedReplyStream(run, prepared, { signal, startedAt = Date.now(), deadlineAt } = {}) {
  return eventStreamResponse(async (send, streamSignal) => {
    const result = { text: '', thinking: '', thinkingSeconds: 0, codeRuns: [], transcripts: {}, build: null, proposal: null, context: null };
    let done = null, failed = false;
    try {
      // Reserve up to 45 seconds for authenticated read + CAS write inside the existing whole deadline.
      const remaining = deadlineAt === undefined ? null : Math.max(1, deadlineAt - Date.now());
      const generationSignal = remaining === null ? streamSignal : AbortSignal.any([
        streamSignal, AbortSignal.timeout(Math.max(1, Math.floor(remaining - Math.min(45000, remaining / 2))))
      ]);
      const response = await run(generationSignal);
      if (!response.ok) {
        const body = await response.json(); send({ type: 'error', message: body.error, status: response.status }); return;
      }
      for await (const record of readSse(response.body, { maxBufferBytes: 4 * 1024 * 1024 })) {
        const event = JSON.parse(record.data);
        if (event.type === 'done') { if (done) throw Error('Duplicate completion'); done = event; continue; }
        if (done) throw Error('Data after completion');
        if (event.type === 'error') failed = true;
        if (event.type === 'text') {
          if (result.thinking && !result.thinkingSeconds) result.thinkingSeconds = Math.max(1, Math.round((Date.now() - startedAt) / 1000));
          result.text += event.delta;
          if (result.text.length > 150000) throw Error('Reply too large');
        }
        if (event.type === 'thinking') result.thinking = (result.thinking + event.delta).slice(0, 20000);
        if (event.type === 'files') for (const file of event.files) result.transcripts[file.path] = file.transcript;
        if (event.type === 'build') { const { type, ...info } = event; result.build = info; }
        if (event.type === 'proposal') { const { type, ...info } = event; result.proposal = info; }
        if (event.type === 'context') result.context = event;
        if (event.type === 'code') {
          const old = result.codeRuns[event.index];
          if (old) result.codeRuns[event.index] = { ...old, ...(event.code?.trim() ? { code: event.code.slice(0, 20000) } : {}),
            ...(event.output !== undefined ? { output: event.output.slice(0, 20000) } : old.output === undefined && event.outcome ? { output: '' } : {}),
            ...(event.outcome ? { outcome: event.outcome } : {}) };
          else if (event.index === result.codeRuns.length && event.code?.trim() && event.index < 10) result.codeRuns.push({
            language: /^[A-Za-z0-9+#._-]{1,20}$/.test(event.language || '') ? event.language.toLowerCase() : 'python', code: event.code.slice(0, 20000),
            ...(event.output !== undefined ? { output: event.output.slice(0, 20000) } : {}), ...(event.outcome ? { outcome: event.outcome } : {}) });
        }
        send(event);
      }
      if (failed) return;
      if (!done || !result.text.trim()) throw new ReplySaveError('Reply ended without a complete answer. Partial reply kept.', 502);
      streamSignal.throwIfAborted();
      const receipt = await prepared.commit(result, streamSignal);
      send({ type: 'saved', ...receipt }); send(done);
    } catch (error) {
      send({ type: 'error', message: error instanceof ReplySaveError ? error.message : 'Server reply saving could not be confirmed. Sync and review; the phone reply is kept.',
        status: error instanceof ReplySaveError ? error.status : 503 });
    }
  }, { signal });
}
