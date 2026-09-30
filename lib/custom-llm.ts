export const CUSTOM_MODEL_ID = "custom-llm";

export type CustomLlm = {
  label: string;
  model: string;
  baseUrl: string;
  apiKey: string;
};

const KEY = "dua.custom-llm.v1";

export function allowedBaseUrl(value: string): string | null {
  const trimmed = value.trim().replace(/\/$/, "");
  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return null;
  }
  const local = url.hostname === "localhost" || url.hostname === "127.0.0.1";
  if (url.protocol === "https:") return trimmed;
  if (url.protocol === "http:" && local) return trimmed;
  return null;
}

export function readCustomLlm(value: unknown): CustomLlm | null {
  if (!value || typeof value !== "object") return null;
  const record = value as Partial<CustomLlm>;
  const model = typeof record.model === "string" ? record.model.trim() : "";
  const baseUrl = typeof record.baseUrl === "string" ? allowedBaseUrl(record.baseUrl) : null;
  if (!model || !baseUrl) return null;
  const label = typeof record.label === "string" && record.label.trim() ? record.label.trim() : "My LLM";
  const apiKey = typeof record.apiKey === "string" ? record.apiKey.trim() : "";
  return {
    label: label.slice(0, 40),
    model: model.slice(0, 120),
    baseUrl,
    apiKey: apiKey.slice(0, 400),
  };
}

export function loadCustomLlm(): CustomLlm | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? readCustomLlm(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

export function saveCustomLlm(value: CustomLlm | null): void {
  if (typeof window === "undefined") return;
  if (!value) {
    window.localStorage.removeItem(KEY);
    return;
  }
  window.localStorage.setItem(KEY, JSON.stringify(value));
}
