import { buildDemoReply, chunkText } from "./demo.ts";
import {
  readAnthropicEvent,
  readGeminiEvent,
  readOpenAIEvent,
  type DeltaResult,
} from "./stream.ts";
import type { ChatTurn } from "./types.ts";

const encoder = new TextEncoder();

export function sseResponse(stream: ReadableStream<Uint8Array>): Response {
  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    },
  });
}

export function encodeEvent(payload: unknown): Uint8Array {
  return encoder.encode(`data: ${JSON.stringify(payload)}\n\n`);
}

export function encodeDone(): Uint8Array {
  return encoder.encode("data: [DONE]\n\n");
}

export function demoStream(turns: ChatTurn[], signal: AbortSignal): ReadableStream<Uint8Array> {
  const latest = [...turns].reverse().find((turn) => turn.role === "user")?.content ?? "";
  const prior = turns
    .filter((turn) => turn.role === "user")
    .slice(0, -1)
    .map((turn) => turn.content);
  const reply = buildDemoReply(latest, prior);
  const chunks = chunkText(reply);

  return new ReadableStream({
    async start(controller) {
      try {
        for (const chunk of chunks) {
          if (signal.aborted) break;
          controller.enqueue(encodeEvent({ delta: chunk }));
          await delay(12, signal);
        }
        controller.enqueue(encodeDone());
        controller.close();
      } catch (error) {
        if (!signal.aborted) {
          controller.enqueue(
            encodeEvent({
              error: error instanceof Error ? error.message : "Demo stream failed.",
            }),
          );
        }
        controller.close();
      }
    },
  });
}

function delay(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener(
      "abort",
      () => {
        clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });
}

async function relayUpstream(options: {
  response: Response;
  signal: AbortSignal;
  read: (data: string) => DeltaResult;
}): Promise<ReadableStream<Uint8Array>> {
  const { response, signal, read } = options;
  if (!response.ok || !response.body) {
    const detail = await safeText(response);
    const message = detail || `The model provider returned ${response.status}.`;
    return errorStream(message);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  return new ReadableStream({
    async start(controller) {
      let buffer = "";
      try {
        while (!signal.aborted) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() ?? "";
          for (const line of lines) {
            const data = sseData(line);
            if (data == null) continue;
            const event = read(data);
            if (event.kind === "delta") controller.enqueue(encodeEvent({ delta: event.text }));
            if (event.kind === "error") controller.enqueue(encodeEvent({ error: event.message }));
            if (event.kind === "done") {
              controller.enqueue(encodeDone());
              controller.close();
              return;
            }
          }
        }
        controller.enqueue(encodeDone());
        controller.close();
      } catch (error) {
        if (!signal.aborted) {
          controller.enqueue(
            encodeEvent({
              error: error instanceof Error ? error.message : "Stream failed.",
            }),
          );
        }
        controller.close();
      } finally {
        reader.releaseLock();
      }
    },
    cancel() {
      void reader.cancel();
    },
  });
}

function sseData(line: string): string | null {
  const trimmed = line.trim();
  if (!trimmed.startsWith("data:")) return null;
  return trimmed.slice(5).trim();
}

async function safeText(response: Response): Promise<string> {
  try {
    const text = (await response.text()).trim();
    if (!text) return "";
    try {
      const json = JSON.parse(text) as {
        error?: { message?: string } | string;
      };
      if (typeof json.error === "string") return json.error;
      if (json.error?.message) return json.error.message;
    } catch {
      return text.slice(0, 400);
    }
    return text.slice(0, 400);
  } catch {
    return "";
  }
}

export function errorStream(message: string): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(encodeEvent({ error: message }));
      controller.enqueue(encodeDone());
      controller.close();
    },
  });
}

export async function openAIStream(options: {
  model: string;
  turns: ChatTurn[];
  systemPrompt: string;
  temperature: number;
  signal: AbortSignal;
  apiKey?: string;
  baseUrl?: string;
}): Promise<ReadableStream<Uint8Array>> {
  const base = (options.baseUrl || process.env.OPENAI_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, "");
  const apiKey = options.apiKey ?? process.env.OPENAI_API_KEY ?? "";
  const response = await fetch(`${base}/chat/completions`, {
    method: "POST",
    signal: options.signal,
    headers: {
      ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: options.model,
      temperature: options.temperature,
      stream: true,
      messages: [
        { role: "system", content: options.systemPrompt },
        ...options.turns,
      ],
    }),
  });
  return relayUpstream({ response, signal: options.signal, read: readOpenAIEvent });
}

export async function anthropicStream(options: {
  model: string;
  turns: ChatTurn[];
  systemPrompt: string;
  temperature: number;
  signal: AbortSignal;
}): Promise<ReadableStream<Uint8Array>> {
  const response = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    signal: options.signal,
    headers: {
      "x-api-key": process.env.ANTHROPIC_API_KEY ?? "",
      "anthropic-version": "2023-06-01",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: options.model,
      max_tokens: 4096,
      temperature: options.temperature,
      system: options.systemPrompt,
      stream: true,
      messages: options.turns,
    }),
  });
  return relayUpstream({ response, signal: options.signal, read: readAnthropicEvent });
}

export async function geminiStream(options: {
  model: string;
  turns: ChatTurn[];
  systemPrompt: string;
  temperature: number;
  signal: AbortSignal;
}): Promise<ReadableStream<Uint8Array>> {
  const key = process.env.GEMINI_API_KEY ?? "";
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(options.model)}:streamGenerateContent?alt=sse&key=${encodeURIComponent(key)}`;
  const response = await fetch(url, {
    method: "POST",
    signal: options.signal,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: options.systemPrompt }] },
      generationConfig: { temperature: options.temperature },
      contents: options.turns.map((turn) => ({
        role: turn.role === "assistant" ? "model" : "user",
        parts: [{ text: turn.content }],
      })),
    }),
  });
  return relayUpstream({ response, signal: options.signal, read: readGeminiEvent });
}
