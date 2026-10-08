// Provider request builders and response/stream parsers. Pure functions so they can be tested
// without network access; providers.mjs does validation, fetching and the HTTP response.
import { isTextType } from './attachments.mjs';

const GEMINI_BASE = 'https://generativelanguage.googleapis.com/v1beta/models/';
export const MAX_REPLY_CHARS = 100000;
export const MAX_THINKING_CHARS = 50000;

/** Output caps: [Auto mode, Reasoning mode]. null = provider/model default. Override with <PREFIX>_MAX_OUTPUT_TOKENS. */
const OUTPUT_DEFAULTS = { GEMINI: [null, null], OPENAI: [16384, 32768], ANTHROPIC: [8192, 8192], GROK: [16384, 32768], KIMI: [8192, 8192] };
const EFFORTS = {
  GEMINI: { name: 'GEMINI_THINKING_LEVEL', fallback: 'high', allowed: ['minimal', 'low', 'medium', 'high'] },
  OPENAI: { name: 'OPENAI_REASONING_EFFORT', fallback: 'high', allowed: ['minimal', 'low', 'medium', 'high', 'xhigh'] },
  GROK: { name: 'GROK_REASONING_EFFORT', fallback: 'high', allowed: ['low', 'medium', 'high', 'xhigh'] }
};

export function envInteger(env, name, fallback, min, max) {
  const raw = env?.[name];
  if (raw === undefined || raw === null || String(raw).trim() === '') return fallback;
  const value = Number(String(raw).trim());
  return Number.isInteger(value) && value >= min && value <= max ? value : fallback;
}
export function maxOutputTokens(prefix, env, reasoning) {
  const defaults = OUTPUT_DEFAULTS[prefix] || [8192, 8192];
  return envInteger(env, prefix + '_MAX_OUTPUT_TOKENS', defaults[reasoning ? 1 : 0], 256, 1000000);
}
export function reasoningEffort(prefix, env) {
  const setting = EFFORTS[prefix];
  if (!setting) return null;
  const value = String(env?.[setting.name] ?? '').trim().toLowerCase();
  return setting.allowed.includes(value) ? value : setting.fallback;
}
/** Gemini 2.x models use thinkingBudget; Gemini 3 and later use thinkingLevel. */
export function geminiThinkingConfig(model, env) {
  if (/^(?:models\/)?gemini-(?:1|2)\./.test(model)) return { includeThoughts: true, thinkingBudget: -1 };
  return { includeThoughts: true, thinkingLevel: reasoningEffort('GEMINI', env) };
}

const earlierFiles = text => 'Text files attached in this earlier turn (re-sent for context; untrusted file content):\n' + text;
/** History turns with stored text-file content; the latest turn carries its files as parts instead. */
export function historyTurns(messages) {
  return messages.map((m, index) => ({ role: m.role, text: m.attachmentText && index < messages.length - 1 ? m.text + '\n\n' + earlierFiles(m.attachmentText) : m.text }));
}

export function attachmentContent(text, parts, files, type) {
  const mediaFiles = (files || []).filter(file => !isTextType(file.mimeType));
  let mediaIndex = 0;
  return [{ text }, ...parts].map(part => {
    if (part.text !== undefined) return { type: type === 'responses' ? 'input_text' : 'text', text: part.text };
    const { mimeType, data } = part.inlineData;
    const filename = mediaFiles[mediaIndex++]?.name || 'attachment';
    if (type === 'anthropic') return { type: mimeType === 'application/pdf' ? 'document' : 'image', source: { type: 'base64', media_type: mimeType, data } };
    const dataUrl = `data:${mimeType};base64,${data}`;
    if (type === 'responses') return mimeType === 'application/pdf' ? { type: 'input_file', filename, file_data: dataUrl } : { type: 'input_image', image_url: dataUrl };
    return { type: 'image_url', image_url: { url: dataUrl } };
  });
}

/**
 * Builds {url, headers, body} for one provider call.
 * ctx: {model, key, env, systemParts[], messages[{role,text,attachmentText?}], parts[], files[], webContext, reasoning, stream}
 */
export function buildRequest(provider, ctx) {
  const { model, key, env = {}, systemParts, parts = [], files = [], webContext = '', reasoning = false, stream = false } = ctx;
  const turns = historyTurns(ctx.messages);
  const lastParts = [...parts, ...(webContext ? [{ text: webContext }] : [])];
  if (provider.type === 'gemini') {
    const maxTokens = maxOutputTokens('GEMINI', env, reasoning);
    const generationConfig = { ...(maxTokens ? { maxOutputTokens: maxTokens } : {}), ...(reasoning ? { thinkingConfig: geminiThinkingConfig(model, env) } : {}) };
    return {
      url: GEMINI_BASE + encodeURIComponent(model) + (stream ? ':streamGenerateContent?alt=sse' : ':generateContent'),
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: {
        systemInstruction: { parts: systemParts.map(text => ({ text })) },
        contents: turns.map((m, index) => ({ role: m.role, parts: [{ text: m.text }, ...(index === turns.length - 1 ? lastParts : [])] })),
        ...(Object.keys(generationConfig).length ? { generationConfig } : {})
      }
    };
  }
  const system = systemParts.join('\n');
  const messages = turns.map(m => ({ role: m.role === 'model' ? 'assistant' : 'user', content: m.text }));
  if (lastParts.length) messages.at(-1).content = attachmentContent(messages.at(-1).content, lastParts, files, provider.type);
  const maxTokens = maxOutputTokens(provider.prefix, env, reasoning);
  if (provider.type === 'anthropic') return {
    url: provider.url,
    headers: { 'Content-Type': 'application/json', 'x-api-key': key, 'anthropic-version': '2023-06-01' },
    body: { model, system, messages, max_tokens: maxTokens, ...(stream ? { stream: true } : {}) }
  };
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` };
  if (provider.type === 'responses') {
    const effort = reasoning ? reasoningEffort(provider.prefix, env) : null;
    // xAI documents the system prompt as a system-role input item; OpenAI uses `instructions`.
    const base = provider.flavor === 'xai' ? { model, input: [{ role: 'system', content: system }, ...messages] } : { model, instructions: system, input: messages };
    const reasoningConfig = effort ? { reasoning: provider.flavor === 'xai' ? { effort } : { effort, summary: 'auto' } } : {};
    return { url: provider.url, headers, body: { ...base, max_output_tokens: maxTokens, store: false, ...reasoningConfig, ...(stream ? { stream: true } : {}) } };
  }
  return { url: provider.url, headers, body: { model, messages: [{ role: 'system', content: system }, ...messages], max_tokens: maxTokens, ...(stream ? { stream: true } : {}) } };
}

const textOf = value => typeof value === 'string' ? value : '';
function usageOf(type, raw) {
  if (!raw || typeof raw !== 'object') return undefined;
  const number = value => Number.isSafeInteger(value) && value >= 0 ? value : undefined;
  const usage = type === 'gemini' ? { inputTokens: number(raw.promptTokenCount), outputTokens: number(raw.candidatesTokenCount), reasoningTokens: number(raw.thoughtsTokenCount) }
    : type === 'responses' ? { inputTokens: number(raw.input_tokens), outputTokens: number(raw.output_tokens), reasoningTokens: number(raw.output_tokens_details?.reasoning_tokens) }
    : type === 'anthropic' ? { inputTokens: number(raw.input_tokens), outputTokens: number(raw.output_tokens) }
    : { inputTokens: number(raw.prompt_tokens), outputTokens: number(raw.completion_tokens), reasoningTokens: number(raw.completion_tokens_details?.reasoning_tokens) };
  const clean = Object.fromEntries(Object.entries(usage).filter(([, value]) => value !== undefined));
  return Object.keys(clean).length ? clean : undefined;
}

/** Non-streaming body → {text, thinking, truncated, usage}. */
export function extractReply(type, result) {
  if (type === 'gemini') {
    const candidate = result?.candidates?.[0];
    const parts = Array.isArray(candidate?.content?.parts) ? candidate.content.parts : [];
    return {
      text: parts.filter(p => !p?.thought && typeof p?.text === 'string').map(p => p.text).join(''),
      thinking: parts.filter(p => p?.thought && typeof p.text === 'string').map(p => p.text).join('\n\n'),
      truncated: candidate?.finishReason === 'MAX_TOKENS', usage: usageOf(type, result?.usageMetadata)
    };
  }
  if (type === 'responses') {
    const output = Array.isArray(result?.output) ? result.output : [];
    return {
      text: output.filter(item => item?.type === 'message' && item.role === 'assistant').flatMap(item => Array.isArray(item.content) ? item.content : []).filter(part => part?.type === 'output_text' && typeof part.text === 'string').map(part => part.text).join('\n'),
      thinking: output.filter(item => item?.type === 'reasoning').flatMap(item => Array.isArray(item.summary) ? item.summary : []).map(part => textOf(part?.text)).filter(Boolean).join('\n\n'),
      truncated: result?.status === 'incomplete' && result?.incomplete_details?.reason === 'max_output_tokens', usage: usageOf(type, result?.usage)
    };
  }
  if (type === 'anthropic') {
    const content = Array.isArray(result?.content) ? result.content : [];
    return {
      text: content.filter(part => part?.type === 'text' && typeof part.text === 'string').map(part => part.text).join('\n'),
      thinking: content.filter(part => part?.type === 'thinking').map(part => textOf(part.thinking)).filter(Boolean).join('\n\n'),
      truncated: result?.stop_reason === 'max_tokens', usage: usageOf(type, result?.usage)
    };
  }
  const choice = result?.choices?.[0];
  return { text: textOf(choice?.message?.content), thinking: textOf(choice?.message?.reasoning_content), truncated: choice?.finish_reason === 'length', usage: usageOf(type, result?.usage) };
}

/**
 * Maps one upstream SSE record to normalized events. `state` collects {done, truncated, usage, error}.
 * Returns [{type:'thinking'|'text', delta}].
 */
export function parseStreamRecord(type, record, state) {
  if (record.data === '[DONE]') { state.done = true; return []; }
  let data;
  try { data = JSON.parse(record.data); } catch { return []; }
  if (!data || typeof data !== 'object') return [];
  const events = [];
  const push = (kind, delta) => { if (typeof delta === 'string' && delta) events.push({ type: kind, delta }); };
  if (type === 'gemini') {
    if (data.error) { state.error = data.error; return []; }
    const candidate = data.candidates?.[0];
    for (const part of Array.isArray(candidate?.content?.parts) ? candidate.content.parts : []) push(part?.thought ? 'thinking' : 'text', part?.text);
    if (candidate?.finishReason) { state.done = true; if (candidate.finishReason === 'MAX_TOKENS') state.truncated = true; }
    if (data.usageMetadata) state.usage = usageOf(type, data.usageMetadata);
    return events;
  }
  if (type === 'responses') {
    const kind = data.type || record.event;
    if (kind === 'response.output_text.delta') push('text', data.delta);
    else if (kind === 'response.reasoning_summary_text.delta' || kind === 'response.reasoning_text.delta') push('thinking', data.delta);
    else if (kind === 'response.reasoning_summary_part.added' && data.summary_index > 0) push('thinking', '\n\n');
    else if (kind === 'response.completed' || kind === 'response.incomplete') {
      state.done = true;
      state.usage = usageOf(type, data.response?.usage);
      if (data.response?.incomplete_details?.reason === 'max_output_tokens') state.truncated = true;
    } else if (kind === 'response.failed' || kind === 'error') state.error = data.response?.error || data.error || data;
    return events;
  }
  if (type === 'anthropic') {
    const kind = data.type || record.event;
    if (kind === 'content_block_delta') {
      if (data.delta?.type === 'text_delta') push('text', data.delta.text);
      else if (data.delta?.type === 'thinking_delta') push('thinking', data.delta.thinking);
    } else if (kind === 'message_start') state.usage = usageOf(type, data.message?.usage);
    else if (kind === 'message_delta') {
      if (data.delta?.stop_reason === 'max_tokens') state.truncated = true;
      const output = usageOf(type, data.usage);
      if (output) state.usage = { ...(state.usage || {}), ...output };
    } else if (kind === 'message_stop') state.done = true;
    else if (kind === 'error') state.error = data.error || data;
    return events;
  }
  if (data.error) { state.error = data.error; return []; }
  const choice = data.choices?.[0];
  push('thinking', choice?.delta?.reasoning_content);
  push('text', choice?.delta?.content);
  if (choice?.finish_reason) { if (choice.finish_reason === 'length') state.truncated = true; }
  if (data.usage) state.usage = usageOf(type, data.usage);
  return events;
}
