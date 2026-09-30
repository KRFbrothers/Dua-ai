import assert from "node:assert/strict";
import test from "node:test";
import { allowedBaseUrl, readCustomLlm } from "../lib/custom-llm.ts";
import { buildDemoReply, chunkText, titleFromMessage } from "../lib/demo.ts";
import { listModels } from "../lib/models.ts";
import { clampTemperature, readAnthropicEvent, readGeminiEvent, readOpenAIEvent, sanitizeTurns } from "../lib/stream.ts";
import { conversationToMarkdown } from "../lib/storage.ts";
import type { Conversation } from "../lib/types.ts";

test("demo reply covers greetings, code, and ordinary questions", () => {
  const hello = buildDemoReply("Hello there");
  assert.match(hello, /Dua Demo/);
  assert.match(hello, /\n\n/);

  const code = buildDemoReply("Write a TypeScript function that trims a title", ["earlier plan"]);
  assert.match(code, /```ts/);
  assert.match(code, /earlier plan/);

  const plain = buildDemoReply("Help me think about a naming choice");
  assert.match(plain, /naming choice/);
});

test("titles stay short and chunking covers the full string", () => {
  assert.equal(titleFromMessage("   "), "New chat");
  assert.equal(titleFromMessage("A".repeat(80)).length <= 52, true);
  assert.equal(chunkText("abcdefghij", 4).join(""), "abcdefghij");
});

test("provider event readers extract text and errors", () => {
  assert.deepEqual(readOpenAIEvent("[DONE]"), { kind: "done" });
  assert.deepEqual(readOpenAIEvent(JSON.stringify({ choices: [{ delta: { content: "Hi" } }] })), {
    kind: "delta",
    text: "Hi",
  });
  assert.equal(
    readAnthropicEvent(
      JSON.stringify({ type: "content_block_delta", delta: { type: "text_delta", text: "Yo" } }),
    ).kind,
    "delta",
  );
  assert.deepEqual(
    readGeminiEvent(JSON.stringify({ candidates: [{ content: { parts: [{ text: "Gem" }] } }] })),
    { kind: "delta", text: "Gem" },
  );
  assert.equal(readOpenAIEvent(JSON.stringify({ error: { message: "nope" } })).kind, "error");
});

test("turns and temperature are bounded", () => {
  assert.equal(clampTemperature("nope"), 0.7);
  assert.equal(clampTemperature(9), 1.5);
  const turns = sanitizeTurns([
    { role: "system", content: "ignore" },
    { role: "user", content: "  hello  " },
    { role: "assistant", content: "" },
  ]);
  assert.equal(turns.length, 1);
  assert.equal(turns[0]?.content, "  hello  ");
});

test("hosted models stay unavailable without keys and demo stays available", () => {
  const models = listModels({});
  assert.equal(models.find((model) => model.id === "dua-demo")?.available, true);
  assert.equal(models.find((model) => model.id === "gpt-4o-mini")?.available, false);
  const unlocked = listModels({ OPENAI_API_KEY: "sk-test", ANTHROPIC_API_KEY: "a", GEMINI_API_KEY: "g" });
  assert.equal(unlocked.every((model) => model.available), true);
});

test("a custom LLM accepts https and local addresses only", () => {
  assert.equal(allowedBaseUrl("https://api.openai.com/v1"), "https://api.openai.com/v1");
  assert.equal(allowedBaseUrl("http://127.0.0.1:11434/v1"), "http://127.0.0.1:11434/v1");
  assert.equal(allowedBaseUrl("http://example.com/v1"), null);
  assert.equal(
    readCustomLlm({ label: "Groq", model: "llama-3.3-70b", baseUrl: "https://api.groq.com/openai/v1", apiKey: "gsk" })
      ?.model,
    "llama-3.3-70b",
  );
  assert.equal(readCustomLlm({ model: "x", baseUrl: "not a url" }), null);
});

test("markdown export includes both sides of a chat", () => {
  const conversation: Conversation = {
    id: "1",
    title: "Naming",
    messages: [
      { id: "u", role: "user", content: "Ideas?", createdAt: 1, status: "complete" },
      { id: "a", role: "assistant", content: "Try Dua.", createdAt: 2, status: "complete" },
    ],
    modelId: "dua-demo",
    systemPrompt: "",
    temperature: 0.7,
    pinned: false,
    createdAt: 1,
    updatedAt: 2,
  };
  const markdown = conversationToMarkdown(conversation);
  assert.match(markdown, /# Naming/);
  assert.match(markdown, /Ideas\?/);
  assert.match(markdown, /Try Dua\./);
});
