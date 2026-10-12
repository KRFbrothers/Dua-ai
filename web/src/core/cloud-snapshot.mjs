import { STORED_PATH, validFileRef } from './file-types.mjs';
import { REPOSITORY } from './github.mjs';
import { validateProjects, validProjectId, defaultProjects } from './projects.mjs';

const MEDIA_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'video/mp4'];
const MODELS = ['Dua', 'Gemini', 'ChatGPT', 'Claude', 'Grok', 'Kimi'];
const BRANCH = /^[A-Za-z0-9._/-]{1,200}$/;
const PROPOSAL_BRANCH = /^dua\/[a-z0-9](?:[a-z0-9-]{0,70}[a-z0-9])?$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SHA = /^[0-9a-f]{40}(?:[0-9a-f]{24})?$/;
const fail = () => { throw Error('Invalid cloud snapshot. Local data was kept.'); };
const object = value => value && typeof value === 'object' && !Array.isArray(value);
const integer = value => Number.isSafeInteger(value) && value >= 0;
const text = (value, max, nonblank = false) => typeof value === 'string' && value.length <= max && (!nonblank || !!value.trim());
const requireValid = value => { if (!value) fail(); };
const optional = (source, key, check, target) => {
  if (source[key] !== undefined) { requireValid(check(source[key])); target[key] = source[key]; }
};

function media(value) {
  requireValid(object(value) && Object.keys(value).every(key => ['path', 'mimeType', 'size', 'duration'].includes(key)) &&
    typeof value.path === 'string' && STORED_PATH.test(value.path) && MEDIA_TYPES.includes(value.mimeType));
  const result = { path: value.path, mimeType: value.mimeType };
  optional(value, 'size', integer, result);
  optional(value, 'duration', n => integer(n) && n <= 60, result);
  return result;
}

function imagine(value) {
  requireValid(object(value) && ['image', 'video'].includes(value.kind) && text(value.prompt, 4000));
  const result = { kind: value.kind, prompt: value.prompt };
  optional(value, 'aspectRatio', v => text(v, 10), result);
  optional(value, 'count', n => integer(n) && n >= 1 && n <= 4, result);
  optional(value, 'status', v => ['pending', 'done', 'failed', 'expired', 'cancelled'].includes(v), result);
  optional(value, 'progress', n => integer(n) && n <= 100, result);
  optional(value, 'startedAt', integer, result);
  optional(value, 'edit', v => typeof v === 'boolean', result);
  optional(value, 'provider', v => text(v, 60), result);
  optional(value, 'model', v => text(v, 100), result);
  optional(value, 'seconds', n => integer(n) && n >= 1 && n <= 60, result);
  optional(value, 'resolution', v => text(v, 20), result);
  optional(value, 'jobId', v => text(v, 2000) && v.length >= 20, result);
  optional(value, 'error', v => text(v, 500), result);
  return result;
}

function codeRun(value) {
  requireValid(object(value) && text(value.code, 20_000, true));
  const result = { code: value.code };
  optional(value, 'language', v => typeof v === 'string' && /^[A-Za-z0-9+#._-]{1,20}$/.test(v), result);
  optional(value, 'output', v => text(v, 20_000), result);
  optional(value, 'outcome', v => ['ok', 'error', 'timeout'].includes(v), result);
  return result;
}

function relativePath(value) {
  return text(value, 400, true) && value === value.trim() && !value.startsWith('/') && !/^[A-Za-z]:/.test(value) &&
    !/[\\\x00-\x1f\x7f]/.test(value) && !value.split('/').some(part => !part || part === '.' || part === '..');
}

function build(value) {
  requireValid(object(value) && typeof value.repository === 'string' && REPOSITORY.test(value.repository) &&
    Array.isArray(value.files) && value.files.length <= 100);
  const paths = new Set();
  const result = { repository: value.repository, files: value.files.map(file => {
    requireValid(object(file) && relativePath(file.path) && !paths.has(file.path)); paths.add(file.path);
    const item = { path: file.path };
    optional(file, 'bytes', n => integer(n) && n <= 2147483647, item);
    optional(file, 'truncated', v => typeof v === 'boolean', item);
    return item;
  }) };
  optional(value, 'branch', v => typeof v === 'string' && (v === '' || BRANCH.test(v)), result);
  return result;
}

function proposal(value) {
  requireValid(object(value) && typeof value.id === 'string' && UUID.test(value.id) &&
    typeof value.repository === 'string' && REPOSITORY.test(value.repository) &&
    typeof value.baseBranch === 'string' && BRANCH.test(value.baseBranch) && typeof value.branch === 'string' && PROPOSAL_BRANCH.test(value.branch) &&
    text(value.title, 120, true) && text(value.summary, 4000) && Array.isArray(value.files) && value.files.length >= 1 && value.files.length <= 50);
  const paths = new Set(); let contentChars = 0;
  const files = value.files.map(file => {
    requireValid(object(file) && relativePath(file.path) && !paths.has(file.path) &&
      !file.path.split('/').some(part => part.toLowerCase() === '.git') &&
      !file.path.toLowerCase().startsWith('.github/workflows/') && file.path.toLowerCase() !== '.gitmodules' &&
      ['create', 'update', 'delete'].includes(file.action));
    paths.add(file.path);
    const item = { path: file.path, action: file.action };
    optional(file, 'content', v => file.action !== 'delete' && text(v, 512 * 1024) && !v.includes('\u0000'), item);
    contentChars += item.content?.length || 0;
    optional(file, 'baseSha', v => typeof v === 'string' && SHA.test(v), item);
    optional(file, 'diff', v => text(v, 20_000), item);
    optional(file, 'additions', n => integer(n) && n <= 2147483647, item);
    optional(file, 'deletions', n => integer(n) && n <= 2147483647, item);
    optional(file, 'truncated', v => typeof v === 'boolean', item);
    return item;
  });
  requireValid(contentChars <= 512 * 1024);
  const result = { id: value.id, repository: value.repository, baseBranch: value.baseBranch, branch: value.branch,
    title: value.title, summary: value.summary, files };
  if (value.pullRequest !== undefined) {
    const pr = value.pullRequest;
    requireValid(object(pr) && integer(pr.number) && pr.number > 0 && pr.number <= 2147483647 &&
      typeof pr.url === 'string' && pr.url.toLowerCase() === ('https://github.com/' + value.repository + '/pull/' + pr.number).toLowerCase());
    const link = { url: pr.url, number: pr.number };
    for (const key of ['draft', 'existing']) optional(pr, key, v => typeof v === 'boolean', link);
    optional(pr, 'branch', v => typeof v === 'string' && (v === '' || PROPOSAL_BRANCH.test(v)), link);
    optional(pr, 'notice', v => text(v, 300), link);
    result.pullRequest = link;
  }
  return result;
}

function message(value) {
  requireValid(object(value) && ['user', 'model'].includes(value.role) && text(value.text, 1048576) && ['complete', 'stopped'].includes(value.state));
  const result = { role: value.role, text: value.text, state: value.state };
  optional(value, 'id', v => text(v, 100, true), result);
  const userFields = ['attachmentText', 'files'];
  const modelFields = ['thinking', 'thinkingSeconds', 'media', 'imagine', 'codeRuns', 'build', 'proposal'];
  requireValid((value.role === 'user' ? modelFields : userFields).every(key => value[key] === undefined));
  if (value.role === 'user') {
    optional(value, 'attachmentText', v => text(v, 32_000), result);
    if (value.files !== undefined) {
      requireValid(Array.isArray(value.files) && value.files.length <= 20 && value.files.every(validFileRef));
      result.files = value.files.map(file => ({ ...file }));
    }
  } else {
    optional(value, 'thinking', v => text(v, 100_000), result);
    optional(value, 'thinkingSeconds', n => integer(n) && n <= 86400, result);
    if (value.media !== undefined) { requireValid(Array.isArray(value.media) && value.media.length <= 4); result.media = value.media.map(media); }
    if (value.imagine !== undefined) result.imagine = imagine(value.imagine);
    if (value.codeRuns !== undefined) { requireValid(Array.isArray(value.codeRuns) && value.codeRuns.length <= 10); result.codeRuns = value.codeRuns.map(codeRun); }
    if (value.build !== undefined) result.build = build(value.build);
    if (value.proposal !== undefined) result.proposal = proposal(value.proposal);
  }
  return result;
}

/** Version 1 stays backward compatible; optional metadata mirrors Android's server snapshot codecs. */
export function validateCloudSnapshot(data) {
  requireValid(object(data) && data.format === 'dua-cloud-snapshot' && data.version === 1 && Array.isArray(data.chats) && data.chats.length <= 100 &&
    Array.isArray(data.memory) && data.memory.length <= 10 && data.memory.every(note => text(note, 200, true)));
  const ids = new Set();
  const deletedChatIds = data.deletedChatIds === undefined ? [] : data.deletedChatIds;
  requireValid(Array.isArray(deletedChatIds) && deletedChatIds.length <= 10_000 && deletedChatIds.every(id => text(id, 200, true)) && new Set(deletedChatIds).size === deletedChatIds.length);
  const projects = data.projects === undefined ? null : validateProjects(data.projects);
  const chats = data.chats.map(chat => {
    requireValid(object(chat) && text(chat.id, 200, true) && !ids.has(chat.id) && !deletedChatIds.includes(chat.id) && integer(chat.updatedAt) && MODELS.includes(chat.model) &&
      text(chat.draft, 8000) && Array.isArray(chat.messages) && chat.messages.length <= 500);
    ids.add(chat.id);
    const result = { id: chat.id, updatedAt: chat.updatedAt, model: chat.model, draft: chat.draft, messages: chat.messages.map(message) };
    optional(chat, 'title', v => text(v, 120, true) && v === v.trim() && !/[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(v), result);
    if (chat.projectId !== undefined) { requireValid(validProjectId(chat.projectId)); result.projectId = chat.projectId; }
    requireValid((projects || defaultProjects()).some(p => p.id === (chat.projectId || 'personal')));
    const messageIds = result.messages.filter(item => item.id !== undefined).map(item => item.id);
    requireValid(new Set(messageIds).size === messageIds.length);
    optional(chat, 'buildRepository', v => typeof v === 'string' && REPOSITORY.test(v), result);
    if (chat.summary !== undefined) {
      requireValid(object(chat.summary) && text(chat.summary.text, 16_000, true) && text(chat.summary.throughId, 100, true));
      result.summary = { text: chat.summary.text, throughId: chat.summary.throughId };
    }
    return result;
  });
  const value = { format: 'dua-cloud-snapshot', version: 1, chats, memory: data.memory.slice() };
  if (data.deletedChatIds !== undefined) value.deletedChatIds = deletedChatIds.slice().sort();
  if (projects) value.projects = projects;
  requireValid(Buffer.byteLength(JSON.stringify(value), 'utf8') <= 4 * 1024 * 1024);
  return value;
}
