import { findModel } from "@/lib/models";
import {
  anthropicStream,
  demoStream,
  errorStream,
  geminiStream,
  openAIStream,
  sseResponse,
} from "@/lib/providers";
import { clampTemperature, sanitizeTurns } from "@/lib/stream";
import { DEFAULT_SYSTEM_PROMPT } from "@/lib/types";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "Send a JSON body." }, { status: 400 });
  }

  const record = (body ?? {}) as {
    model?: unknown;
    messages?: unknown;
    systemPrompt?: unknown;
    temperature?: unknown;
  };

  const modelId = typeof record.model === "string" ? record.model : "dua-demo";
  const model = findModel(modelId);
  if (!model) {
    return Response.json({ error: "Unknown model." }, { status: 400 });
  }
  if (!model.available) {
    return Response.json(
      { error: model.unavailableReason ?? "This model is unavailable." },
      { status: 400 },
    );
  }

  const turns = sanitizeTurns(record.messages);
  if (!turns.some((turn) => turn.role === "user")) {
    return Response.json({ error: "Add a user message." }, { status: 400 });
  }

  const systemPrompt =
    typeof record.systemPrompt === "string" && record.systemPrompt.trim()
      ? record.systemPrompt.slice(0, 4000)
      : DEFAULT_SYSTEM_PROMPT;
  const temperature = clampTemperature(record.temperature);

  try {
    if (model.provider === "demo") {
      return sseResponse(demoStream(turns, request.signal));
    }
    if (model.provider === "openai") {
      return sseResponse(
        await openAIStream({ model: model.id, turns, systemPrompt, temperature, signal: request.signal }),
      );
    }
    if (model.provider === "anthropic") {
      return sseResponse(
        await anthropicStream({
          model: model.id,
          turns,
          systemPrompt,
          temperature,
          signal: request.signal,
        }),
      );
    }
    return sseResponse(
      await geminiStream({ model: model.id, turns, systemPrompt, temperature, signal: request.signal }),
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "The model request failed.";
    return sseResponse(errorStream(message));
  }
}
