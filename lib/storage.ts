import {
  DEFAULT_SYSTEM_PROMPT,
  DEFAULT_TEMPERATURE,
  type Conversation,
} from "./types.ts";
import { DEMO_MODEL_ID } from "./models.ts";

export const STORAGE_KEY = "dua.conversations.v1";

export type WorkspaceState = {
  conversations: Conversation[];
  activeId: string | null;
  theme: "light" | "dark";
  draftModelId: string;
  draftSystemPrompt: string;
  draftTemperature: number;
};

export const emptyWorkspace = (): WorkspaceState => ({
  conversations: [],
  activeId: null,
  theme: "dark",
  draftModelId: DEMO_MODEL_ID,
  draftSystemPrompt: DEFAULT_SYSTEM_PROMPT,
  draftTemperature: DEFAULT_TEMPERATURE,
});

export function loadWorkspace(): WorkspaceState {
  if (typeof window === "undefined") return emptyWorkspace();
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return emptyWorkspace();
    const parsed = JSON.parse(raw) as Partial<WorkspaceState>;
    return {
      conversations: Array.isArray(parsed.conversations) ? parsed.conversations : [],
      activeId: typeof parsed.activeId === "string" ? parsed.activeId : null,
      theme: parsed.theme === "light" ? "light" : "dark",
      draftModelId:
        typeof parsed.draftModelId === "string" ? parsed.draftModelId : DEMO_MODEL_ID,
      draftSystemPrompt:
        typeof parsed.draftSystemPrompt === "string"
          ? parsed.draftSystemPrompt
          : DEFAULT_SYSTEM_PROMPT,
      draftTemperature:
        typeof parsed.draftTemperature === "number"
          ? parsed.draftTemperature
          : DEFAULT_TEMPERATURE,
    };
  } catch {
    return emptyWorkspace();
  }
}

export function saveWorkspace(state: WorkspaceState): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function conversationToMarkdown(conversation: Conversation): string {
  const lines = [`# ${conversation.title}`, ""];
  for (const message of conversation.messages) {
    const who = message.role === "user" ? "You" : "Dua";
    lines.push(`## ${who}`, "", message.content, "");
  }
  return lines.join("\n").trim() + "\n";
}
