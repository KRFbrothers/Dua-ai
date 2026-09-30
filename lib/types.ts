export type Role = "user" | "assistant";

export type MessageStatus = "complete" | "streaming" | "error" | "stopped";

export type ChatMessage = {
  id: string;
  role: Role;
  content: string;
  createdAt: number;
  status: MessageStatus;
  modelId?: string;
  imageName?: string;
};

export type Conversation = {
  id: string;
  title: string;
  messages: ChatMessage[];
  modelId: string;
  systemPrompt: string;
  temperature: number;
  pinned: boolean;
  createdAt: number;
  updatedAt: number;
};

export type ProviderId = "demo" | "openai" | "anthropic" | "gemini";

export type ModelInfo = {
  id: string;
  label: string;
  provider: ProviderId;
  description: string;
  available: boolean;
  unavailableReason?: string;
};

export type ChatTurn = {
  role: Role;
  content: string;
};

export const DEFAULT_SYSTEM_PROMPT =
  "You are Dua, a clear and careful assistant. Answer directly, use markdown when it helps, and ask a focused follow-up only when it would change the answer.";

export const DEFAULT_TEMPERATURE = 0.7;
