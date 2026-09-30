export type DeltaResult =
  | { kind: "delta"; text: string }
  | { kind: "done" }
  | { kind: "ignore" }
  | { kind: "error"; message: string };

export function readOpenAIEvent(data: string): DeltaResult {
  if (data === "[DONE]") return { kind: "done" };
  try {
    const json = JSON.parse(data) as {
      error?: { message?: string };
      choices?: Array<{ delta?: { content?: string | null } }>;
    };
    if (json.error?.message) return { kind: "error", message: json.error.message };
    const text = json.choices?.[0]?.delta?.content ?? "";
    return text ? { kind: "delta", text } : { kind: "ignore" };
  } catch {
    return { kind: "ignore" };
  }
}

export function readAnthropicEvent(data: string): DeltaResult {
  try {
    const json = JSON.parse(data) as {
      type?: string;
      delta?: { type?: string; text?: string };
      error?: { message?: string };
    };
    if (json.type === "error") {
      return { kind: "error", message: json.error?.message || "Anthropic request failed." };
    }
    if (json.type === "message_stop") return { kind: "done" };
    if (json.type === "content_block_delta" && json.delta?.text) {
      return { kind: "delta", text: json.delta.text };
    }
    return { kind: "ignore" };
  } catch {
    return { kind: "ignore" };
  }
}

export function readGeminiEvent(data: string): DeltaResult {
  try {
    const json = JSON.parse(data) as {
      error?: { message?: string };
      candidates?: Array<{
        content?: { parts?: Array<{ text?: string }> };
      }>;
    };
    if (json.error?.message) return { kind: "error", message: json.error.message };
    const text =
      json.candidates?.[0]?.content?.parts?.map((part) => part.text ?? "").join("") ?? "";
    return text ? { kind: "delta", text } : { kind: "ignore" };
  } catch {
    return { kind: "ignore" };
  }
}

export function clampTemperature(value: unknown): number {
  const number = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(number)) return 0.7;
  return Math.min(1.5, Math.max(0, number));
}

export function sanitizeTurns(
  value: unknown,
): Array<{ role: "user" | "assistant"; content: string }> {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is { role: string; content: string } => {
      if (!item || typeof item !== "object") return false;
      const candidate = item as { role?: unknown; content?: unknown };
      return (
        (candidate.role === "user" || candidate.role === "assistant") &&
        typeof candidate.content === "string" &&
        candidate.content.trim().length > 0
      );
    })
    .slice(-40)
    .map((item) => ({
      role: item.role as "user" | "assistant",
      content: item.content.slice(0, 12000),
    }));
}
